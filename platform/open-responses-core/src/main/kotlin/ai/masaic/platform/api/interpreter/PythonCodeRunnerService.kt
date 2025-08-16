package ai.masaic.platform.api.interpreter

import ai.masaic.openresponses.api.exception.CodeRunnerServiceNotFoundException
import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.ToolService
import ai.masaic.openresponses.tool.mcp.CallToolResponse
import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.openresponses.tool.mcp.MCPToolExecutor
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import java.util.*

interface CodeRunnerService {
    suspend fun runCode(request: CodeExecuteReq): CodeExecResult
}

class PythonCodeRunnerService(
    private val mcpTool: MCPTool,
    private val toolService: ToolService,
    private val mcpToolExecutor: MCPToolExecutor
) : CodeRunnerService {
    private var codeRunnerTool: String
    private var codeRunnerToolDef: ToolDefinition
    private val mapper = jacksonObjectMapper()
    private val log = KotlinLogging.logger { }

    init {
        runBlocking {
            val tools = toolService.getRemoteMcpTools(mcpTool)
            codeRunnerTool = MCPServerInfo(mcpTool.serverLabel, mcpTool.serverUrl).qualifiedToolName("run_code")
            codeRunnerToolDef = toolService.findToolByName(codeRunnerTool)
                ?: throw IllegalArgumentException("Tool $codeRunnerTool not found.")
        }
    }

    override suspend fun runCode(request: CodeExecuteReq): CodeExecResult {
        require(!request.funName.isNullOrBlank()) { "request must contain funName" }
        val codeStr = String(Base64.getDecoder().decode(request.encodedCode), Charsets.UTF_8)
        val paramsJsonStr = try {String(Base64.getDecoder().decode(request.encodedJsonParams), Charsets.UTF_8)} catch (ex: IllegalArgumentException) {String(request.encodedJsonParams!!.toByteArray(), Charsets.UTF_8)}
        val depsJson = mapper.writeValueAsString(request.deps)
        val codeJson = mapper.writeValueAsString(codeStr)

        val code = assembleCode(
            CodeAssembleAttributes(
                name = request.funName,
                deps = depsJson,
                userCode = codeJson,
                params = paramsJsonStr
            )
        )
        val toolResult =
            mcpToolExecutor.executeTool(codeRunnerToolDef, mapper.writeValueAsString(mapOf("code" to code)), null, null)
                ?: "no response from $codeRunnerTool"
        return extractCodeRunResult(toolResult)
    }

    private fun assembleCode(request: CodeAssembleAttributes): String {
        val code = """
import sys, subprocess, json, base64

# 1) deps from registry
for _pkg in ${request.deps}:
    if _pkg:
        subprocess.run([sys.executable, "-m", "pip", "install", _pkg],
                       check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

# 2) params
params = json.loads('''${request.params}''')

# 3) load analyst code at top level (no indentation coupling)
_code = ${request.userCode}
_code = _code.replace("\r\n","\n").replace("\r","\n").expandtabs(4)
exec(compile(_code, "<user_code>", "exec"), globals(), globals())

# 4) call run(params) or fallback to ENTRYPOINT(**params)
ENTRYPOINT = "${request.name}"
if "run" in globals() and callable(globals()["run"]):
    result = globals()["run"](params)
else:
    fn = globals().get(ENTRYPOINT)
    if fn is None or not callable(fn):
        raise RuntimeError(f"ENTRYPOINT '{ENTRYPOINT}' not found and no run(params) provided")
    result = fn(**params)

# 5) print result as a single JSON line (easy to parse)
print(json.dumps({"function_output": result}, ensure_ascii=False), end="")   
        """.trimIndent()

        log.info { "======== Final code for execution=======" }
        log.info { code }
        log.info { "========================================" }
        return code
    }

    private fun extractCodeRunResult(toolResult: String): CodeExecResult {
        val callToolResponse: CallToolResponse = mapper.readValue(toolResult)
        log.debug { "code execution raw response: $callToolResponse" }
        val result = if (callToolResponse.isError) {
            CodeExecResult(functionOutput = mapOf("error" to callToolResponse.content))
        } else {
            val interpreterResult: CodeInterpreterResult = mapper.readValue(callToolResponse.content)
            val realOutput = if (interpreterResult.stdout.isNotEmpty() && interpreterResult.stdout.size == 1) {
                val output = mapper.readTree(interpreterResult.stdout[0])
                if (output.has("function_output")) mapper.treeToValue(
                    output,
                    CodeExecResult::class.java
                ) else CodeExecResult(functionOutput = mapOf("error" to output))
            } else if (interpreterResult.error != null) {
                CodeExecResult(
                    error = CodeExecError(
                        code = CodeExecErrorCodes.ERROR,
                        message = "code interpreter responded with error",
                        trace = interpreterResult.error
                    )
                )
            } else if (interpreterResult.stderr.isNotEmpty()) {
                CodeExecResult(
                    error = CodeExecError(
                        code = CodeExecErrorCodes.STD_ERROR,
                        message = "code interpreter responded with stderr",
                        trace = interpreterResult.stderr
                    )
                )
            } else if (interpreterResult.results.isNotEmpty()) {
                CodeExecResult(
                    error = CodeExecError(
                        code = CodeExecErrorCodes.UNEXPECTED_RESULT,
                        message = "code interpreter responded with unexpected result",
                        trace = interpreterResult.results
                    )
                )
            } else {
                CodeExecResult(
                    error = CodeExecError(
                        code = CodeExecErrorCodes.INTERNAL_ERROR,
                        message = "unable to handle response",
                        trace = interpreterResult
                    )
                )
            }
            realOutput
        }
        log.info { "code execution result: $result" }
        return result
    }
}

class NoOpCodeRunnerService : CodeRunnerService {
    override suspend fun runCode(request: CodeExecuteReq): CodeExecResult {
        throw CodeRunnerServiceNotFoundException("code runner service is not available.")
    }
}

data class CodeExecuteReq(
    val funName: String? = null,
    val deps: List<String> = emptyList(),
    val encodedCode: String,
    val encodedJsonParams: String? = null
)

data class CodeAssembleAttributes(val name: String, val deps: String, val userCode: String, val params: String? = null)

data class CodeInterpreterErrorData(
    val name: String? = null,
    val value: String? = null,
    val traceback: String? = null
)

data class CodeInterpreterResult(
    val stdout: List<String> = emptyList(),
    val stderr: List<String> = emptyList(),
    val results: List<String> = emptyList(),
    val error: CodeInterpreterErrorData? = null
)

data class CodeExecResult(
    @JsonProperty("function_output") val functionOutput: Map<String, Any> = emptyMap(),
    val error: CodeExecError? = null
)

data class CodeExecError(val code: String, val message: String, val trace: Any)

object CodeExecErrorCodes {
    const val ERROR = "ERROR"
    const val STD_ERROR = "STD_ERROR"
    const val UNEXPECTED_RESULT = "UNEXPECTED_RESULT"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
