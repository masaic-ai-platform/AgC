package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.NativeToolDefinition
import ai.masaic.openresponses.tool.ToolParamsAccessor
import ai.masaic.openresponses.tool.ToolProgressEventMeta
import ai.masaic.openresponses.tool.UnifiedToolContext
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.model.PromptGenerationRequest
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.service.messages
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import org.springframework.http.codec.ServerSentEvent

class SystemPromptGeneratorTool(
    private val modelSettings: ModelSettings,
    private val modelService: ModelService,
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
        val request: PromptGenerationRequest = mapper.readValue(arguments)
        val messages = addMessages(request)
        return callModel(paramsAccessor, client, messages)
    }

    suspend fun generatePrompt(
        request: PromptGenerationRequest,
        modelSettings: ModelSettings,
    ): String {
        val messages = addMessages(request)
        return callModel(modelSettings, messages)
    }

    private suspend fun addMessages(
        request: PromptGenerationRequest,
    ): List<Map<String, String>> {
        val generatePromptMetaPrompt =
            """
You are an elite prompt engineer.

OVERVIEW  
You will receive up to three labelled blocks:

  • TASK DESCRIPTION – plain-language explanation of what the model should do or mentions explicit edits the user wants.
  • EXISTING PROMPT   – (optional) the current prompt to be refined.   

YOUR JOB  
► If an EXISTING PROMPT is present, update it so that all TASK DESCRIPTION are satisfied **while preserving the original structure and intent wherever not contradicted**.  
► If no EXISTING PROMPT is supplied, craft a brand-new prompt that fulfils the TASK DESCRIPTION.  
► Always return **exactly one** finished execution prompt, ready for a language model to follow.

RECOMMENDED PROMPT STRUCTURE  
1. **Title line** – one concise sentence summarising the task.  
2. **Steps** – 3-7 bullet points, each starting with an imperative verb (Accept…, Validate…, Compute…).  
3. **Output format** – short block describing the required output.  
4. **Examples** – at least two `Input:` / `Output:` pairs.  
5. **Reminder** – a bold sentence beginning “Reminder:” that restates any critical rule(s).

STYLE & RULES  
• Use clear, unambiguous English and markdown bullets/headings.  
• Incorporate every constraint from TASK DESCRIPTION.    
• Do **not** output anything except the finished execution prompt—no commentary, fences, or JSON.

INPUT BLOCKS  
────────────────────────────────────────
TASK DESCRIPTION  
${request.description}

EXISTING PROMPT   (optional)
${request.existingPrompt}
────────────────────────────────────────
            """.trimIndent()

        return messages {
            systemMessage(generatePromptMetaPrompt)
            userMessage("Generate the system prompt.")
        }
    }
}
