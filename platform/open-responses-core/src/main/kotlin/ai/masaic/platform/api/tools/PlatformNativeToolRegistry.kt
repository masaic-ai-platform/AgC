package ai.masaic.platform.api.tools

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.tool.*
import ai.masaic.platform.api.interpreter.CodeExecuteReq
import ai.masaic.platform.api.interpreter.CodeRunnerService
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.client.OpenAIClient
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent

class PlatformNativeToolRegistry(
    private val objectMapper: ObjectMapper,
    responseStore: ResponseStore,
    private val platformNativeTools: List<PlatformNativeTool>,
    private val codeRunnerService: CodeRunnerService,
) : NativeToolRegistry(objectMapper, responseStore) {
    private val log = KotlinLogging.logger {}

    init {
        platformNativeTools.forEach {
            if (it.isToolDefAvailable()) {
                toolRepository[it.toolName()] = it.provideToolDef()
            }
        }
    }

    override suspend fun executeTool(
        resolvedName: String,
        arguments: String,
        paramsAccessor: ToolParamsAccessor,
        client: OpenAIClient,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
        toolMetadata: Map<String, Any>,
        context: UnifiedToolContext,
    ): String? {
        val tool = toolRepository[resolvedName] ?: return null
        val originalName = toolMetadata["originalName"] as? String ?: resolvedName
        log.debug("Executing native tool '$originalName' (resolved to '$resolvedName') with arguments: $arguments")

        val toolResponse =
            if (tool is PyFunToolDefinition) {
                val codeExecResult =
                    codeRunnerService.runCode(
                        CodeExecuteReq(
                            funName = tool.name,
                            deps = tool.deps,
                            encodedCode = tool.code,
                            encodedJsonParams = arguments,
                            pyInterpreterServer = tool.pyInterpreterServer,
                        ),
                        eventEmitter,
                    )
                objectMapper.writeValueAsString(codeExecResult)
            } else {
                val platformNativeTool = platformNativeTools.find { it.toolName() == resolvedName }
                platformNativeTool?.executeTool(
                    resolvedName,
                    arguments,
                    paramsAccessor,
                    client,
                    eventEmitter,
                    toolMetadata,
                    context,
                ) ?: super.executeTool(
                    resolvedName,
                    arguments,
                    paramsAccessor,
                    client,
                    eventEmitter,
                    toolMetadata,
                    context,
                )
            }

        log.debug { "toolResponse: $toolResponse" }
        return toolResponse
    }
}
