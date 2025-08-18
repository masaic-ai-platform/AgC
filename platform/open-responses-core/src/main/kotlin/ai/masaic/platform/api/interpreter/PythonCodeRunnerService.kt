package ai.masaic.platform.api.interpreter

import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.api.model.PyInterpreterServer
import ai.masaic.openresponses.tool.ToolService
import ai.masaic.openresponses.tool.mcp.CallToolResponse
import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.openresponses.tool.mcp.MCPToolExecutor
import ai.masaic.platform.api.config.PyInterpreterSettings
import ai.masaic.platform.api.config.SystemSettingsType
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import java.util.*

interface CodeRunnerService {
    suspend fun runCode(
        request: CodeExecuteReq,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): CodeExecResult
}

class PythonCodeRunnerService(
    private val interpreterSettings: PyInterpreterSettings,
    private val toolService: ToolService,
    private val mcpToolExecutor: MCPToolExecutor,
) : CodeRunnerService {
    private var mcpTool: MCPTool? = null
    private val mapper = jacksonObjectMapper()
    private val log = KotlinLogging.logger { }

    init {
        runBlocking {
            if (interpreterSettings.systemSettingsType == SystemSettingsType.DEPLOYMENT_TIME) {
                mcpTool = interpreterSettings.mcpTool()
                val tools = toolService.getRemoteMcpTools(interpreterSettings.mcpTool())
            }
        }
    }

    override suspend fun runCode(
        request: CodeExecuteReq,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): CodeExecResult {
        require(!request.funName.isNullOrBlank()) { "request must contain funName" }
        val tobeUsedMCPTool: MCPTool =
            request.pyInterpreterServer?.let {
                toolService.getRemoteMcpTools(interpreterSettings.mcpTool(it))
                interpreterSettings.mcpTool(it)
            } ?: mcpTool ?: throw IllegalArgumentException(
                "Python interpreter connection settings are neither available at server nor in the request",
            )
        val codeRunnerTool = MCPServerInfo(tobeUsedMCPTool.serverLabel, tobeUsedMCPTool.serverUrl).qualifiedToolName("run_code")
        val codeRunnerToolDef =
            toolService.findToolByName(codeRunnerTool)
                ?: throw IllegalArgumentException("Tool $codeRunnerTool not found.")

        val codeStr = String(Base64.getDecoder().decode(request.encodedCode), Charsets.UTF_8)
        val paramsJsonStr =
            if (request.encodedJsonParams.isNullOrBlank()) {
                ""
            } else {
                try {
                    String(Base64.getDecoder().decode(request.encodedJsonParams), Charsets.UTF_8)
                } catch (ex: IllegalArgumentException) {
                    String(request.encodedJsonParams.toByteArray(), Charsets.UTF_8)
                }
            }
        val depsJson = mapper.writeValueAsString(request.deps)
        val codeJson = mapper.writeValueAsString(codeStr)

        val attributes =
            CodeAssembleAttributes(
                name = request.funName,
                deps = depsJson,
                userCode = codeJson,
                params = paramsJsonStr,
            )
        val code = if (paramsJsonStr.isEmpty()) assembleCodeWithoutParams(attributes) else assembleCodeWithParams(attributes)
        val eventPrefix = "response.agc.${request.funName}"
        emitEvent(eventEmitter, "$eventPrefix.executing", code)

        val toolResult =
            mcpToolExecutor.executeTool(codeRunnerToolDef, mapper.writeValueAsString(mapOf("code" to code)), null, null)
                ?: "no response from $codeRunnerTool"
        val codeExecResult = extractCodeRunResult(toolResult)
        return codeExecResult
    }

    private fun assembleCodeWithoutParams(request: CodeAssembleAttributes): String {
        val code =
            """
import sys, subprocess, json, base64, io, contextlib

# 1) deps from registry
for _pkg in ${request.deps}:
    if _pkg:
        subprocess.run([sys.executable, "-m", "pip", "install", _pkg],
                       check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

# 2) load analyst code at top level
_code = ${request.userCode}
_code = _code.replace("\r\n", "\n").replace("\r", "\n").expandtabs(4)

globals_dict = {}

# Capture user stdout
user_stdout = io.StringIO()
with contextlib.redirect_stdout(user_stdout):
    exec(compile(_code, "<user_code>", "exec"), globals_dict, globals_dict)

# 3) call run(params) or fallback to ENTRYPOINT(**params)
ENTRYPOINT = "${request.name}"
if "run" in globals_dict and callable(globals_dict["run"]):
    result = globals_dict["run"]()
else:
    fn = globals_dict.get(ENTRYPOINT)
    if fn is None or not callable(fn):
        raise RuntimeError(f"ENTRYPOINT '{ENTRYPOINT}' not found and no run(params) provided")
    result = fn()

# 4) capture any intermediate prints from user code
user_prints = user_stdout.getvalue()

# 5) print *only deterministic JSON* to stdout
print(json.dumps({
    "function_output": result,
    "user_stdout": user_prints.strip() if user_prints else None
}, ensure_ascii=False))
            """.trimIndent()

        log.debug { "======== Final code for execution=======" }
        log.debug { code }
        log.debug { "========================================" }
        return code
    }

    private fun assembleCodeWithParams(request: CodeAssembleAttributes): String {
        val code =
            """
import sys, subprocess, json, base64, io, contextlib

# 1) deps from registry
for _pkg in ${request.deps}:
    if _pkg:
        subprocess.run([sys.executable, "-m", "pip", "install", _pkg],
                       check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

# 2) params
params = json.loads('''${request.params}''')

# 3) prepare user code
_code = ${request.userCode}
_code = _code.replace("\r\n", "\n").replace("\r", "\n").expandtabs(4)

globals_dict = {}

# 4) execute user code (function definitions, imports, etc.)
exec(compile(_code, "<user_code>", "exec"), globals_dict, globals_dict)

# 5) find entrypoint
ENTRYPOINT = "${request.name}"
fn = None
if "run" in globals_dict and callable(globals_dict["run"]):
    fn = globals_dict["run"]
else:
    fn = globals_dict.get(ENTRYPOINT)
    if fn is None or not callable(fn):
        raise RuntimeError(f"ENTRYPOINT '{ENTRYPOINT}' not found and no run(params) provided")

# 6) capture stdout during function call
user_stdout = io.StringIO()
with contextlib.redirect_stdout(user_stdout):
    result = fn(**params)

# 7) collect captured prints
user_prints = user_stdout.getvalue()

# 8) deterministic JSON output
print(json.dumps({
    "function_output": result,
    "user_stdout": user_prints.strip() if user_prints else None
}, ensure_ascii=False), end="")
            """.trimIndent()

        log.debug { "======== Final code for execution=======" }
        log.debug { code }
        log.debug { "========================================" }
        return code
    }

    private fun extractCodeRunResult(toolResult: String): CodeExecResult {
        val callToolResponse: CallToolResponse = mapper.readValue(toolResult)
        log.debug { "code execution raw response: $callToolResponse" }
        val result =
            if (callToolResponse.isError) {
                CodeExecResult(functionOutput = mapOf("error" to callToolResponse.content))
            } else {
                val interpreterResult: CodeInterpreterResult = mapper.readValue(callToolResponse.content)
                val realOutput =
                    if (interpreterResult.stdout.isNotEmpty() && interpreterResult.stdout.size == 1) {
                        val output = mapper.readTree(interpreterResult.stdout[0])
                        if (output.has("function_output")) {
                            mapper.treeToValue(
                                output,
                                CodeExecResult::class.java,
                            )
                        } else {
                            CodeExecResult(functionOutput = mapOf("error" to output))
                        }
                    } else if (interpreterResult.error != null) {
                        CodeExecResult(
                            error =
                                CodeExecError(
                                    code = CodeExecErrorCodes.ERROR,
                                    message = "code interpreter responded with error",
                                    trace = interpreterResult.error,
                                ),
                        )
                    } else if (interpreterResult.stderr.isNotEmpty()) {
                        CodeExecResult(
                            error =
                                CodeExecError(
                                    code = CodeExecErrorCodes.STD_ERROR,
                                    message = "code interpreter responded with stderr",
                                    trace = interpreterResult.stderr,
                                ),
                        )
                    } else if (interpreterResult.results.isNotEmpty()) {
                        CodeExecResult(
                            error =
                                CodeExecError(
                                    code = CodeExecErrorCodes.UNEXPECTED_RESULT,
                                    message = "code interpreter responded with unexpected result",
                                    trace = interpreterResult.results,
                                ),
                        )
                    } else {
                        CodeExecResult(
                            error =
                                CodeExecError(
                                    code = CodeExecErrorCodes.INTERNAL_ERROR,
                                    message = "unable to handle response",
                                    trace = interpreterResult,
                                ),
                        )
                    }
                realOutput
            }
        log.info { "code execution result: $result" }
        return result
    }

    private fun emitEvent(
        eventEmitter: (ServerSentEvent<String>) -> Unit,
        eventName: String,
        code: String,
    ) {
        eventEmitter.invoke(
            ServerSentEvent
                .builder<String>()
                .event(eventName)
                .data(
                    mapper.writeValueAsString(
                        mapOf<String, String>(
                            "item_id" to (UUID.randomUUID().toString()),
                            "output_index" to "0",
                            "type" to eventName,
                            "code" to code,
                        ),
                    ),
                ).build(),
        )
    }
}

data class CodeExecuteReq(
    val funName: String? = null,
    val deps: List<String> = emptyList(),
    val encodedCode: String,
    val encodedJsonParams: String? = null,
    @JsonProperty("code_interpreter")
    val pyInterpreterServer: PyInterpreterServer? = null,
)

data class CodeAssembleAttributes(
    val name: String,
    val deps: String,
    val userCode: String,
    val params: String? = null,
)

data class CodeInterpreterErrorData(
    val name: String? = null,
    val value: String? = null,
    val traceback: String? = null,
)

data class CodeInterpreterResult(
    val stdout: List<String> = emptyList(),
    val stderr: List<String> = emptyList(),
    val results: List<String> = emptyList(),
    val error: CodeInterpreterErrorData? = null,
)

data class CodeExecResult(
    @JsonProperty("function_output") val functionOutput: Map<String, Any> = emptyMap(),
    @JsonProperty("user_stdout") val debugStatements: String? = null,
    val error: CodeExecError? = null,
)

data class CodeExecError(
    val code: String,
    val message: String,
    val trace: Any,
)

object CodeExecErrorCodes {
    const val ERROR = "ERROR"
    const val STD_ERROR = "STD_ERROR"
    const val UNEXPECTED_RESULT = "UNEXPECTED_RESULT"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
