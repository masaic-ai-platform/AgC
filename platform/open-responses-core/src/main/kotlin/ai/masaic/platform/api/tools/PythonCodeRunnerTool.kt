package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.NativeToolDefinition
import ai.masaic.openresponses.tool.ToolParamsAccessor
import ai.masaic.openresponses.tool.UnifiedToolContext
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.interpreter.PythonCodeRunnerService
import com.openai.client.OpenAIClient
import org.springframework.http.codec.ServerSentEvent

class PythonCodeRunnerTool(
    private val pythonCodeRunnerService: PythonCodeRunnerService,
) : PlatformNativeTool(PlatformToolsNames.PY_CODE_RUNNER) {
    override fun provideToolDef(): NativeToolDefinition =
        nativeToolDefinition {
            name(toolName)
            description("This tool is runs the given python code")
            parameters {
                property(
                    name = "encodedCode",
                    type = "string",
                    description = "code encoded as base64 UTF-8 encoded string",
                    required = true,
                )
                property(
                    name = "encodedParams",
                    type = "string",
                    description = "If code expects input arguments then provide them as base64 UTF-8 encoded JSON",
                    required = false,
                )
                additionalProperties = false
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
    ): String? = null
}
