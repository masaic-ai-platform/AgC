package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.NativeToolDefinition
import ai.masaic.openresponses.tool.ToolParamsAccessor
import ai.masaic.openresponses.tool.ToolProgressEventMeta
import ai.masaic.openresponses.tool.UnifiedToolContext
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.service.ModelService
import com.openai.client.OpenAIClient
import org.springframework.http.codec.ServerSentEvent

class MCPServerConfigurationTool(
    modelSettings: ModelSettings,
    modelService: ModelService,
) : ModelDepPlatformNativeTool(PlatformToolsNames.SYSTEM_PROMPT_GENERATOR_TOOL, modelService, modelSettings) {
    override fun provideToolDef(): NativeToolDefinition =
        nativeToolDefinition {
            name(toolName)
            description("Generate the system prompt based upon the described requirements.")
            parameters {
                property(
                    name = "description",
                    type = "string",
                    description = "Describe the requirements that expected prompt should meet.",
                    required = true,
                )
                property(
                    name = "existingPrompt",
                    type = "string",
                    description = "Optionally, provide existing prompt if you have. Then modifications will happen on top of it.",
                    required = false,
                )
                additionalProperties = false
            }
            eventMeta(ToolProgressEventMeta(infix = "agc"))
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
        TODO("Not yet implemented")
    }
}
