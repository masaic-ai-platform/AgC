package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.model.CreateResponseRequest
import ai.masaic.openresponses.api.service.ResponseFacadeService
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.api.service.ResponseProcessingInput
import ai.masaic.openresponses.api.service.ResponseProcessingResult
import ai.masaic.openresponses.api.utils.ResponsesUtils
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.config.SystemSettingsType
import ai.masaic.platform.api.model.PlatformAgent
import com.openai.models.responses.Response
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent

class AskAgentService(
    private val agentService: AgentService,
    private val responseFacadeService: ResponseFacadeService,
    private val modelSettings: ModelSettings,
) {
    private val log = KotlinLogging.logger { }

    /**
     * Asks an agent a question and returns the response synchronously (non-streaming).
     * 
     * @param request The request containing agent name, query, and context
     * @return AskAgentResponse containing the agent's response
     * @throws ResponseProcessingException if the agent is not found or processing fails
     */
    suspend fun askAgent(request: AskAgentRequest): AskAgentResponse {
        require(request.agentName.isNotEmpty()) { "agent name is mandatory. Provide valid agent name." }
        log.info { "Processing ask agent request for agent: ${request.agentName}" }
        
        // Get the agent configuration
        val agent =
            agentService.getAgent(agentName = request.agentName) 
                ?: throw ResponseProcessingException("Agent: ${request.agentName} is not found.")
        
        log.debug { "Found agent: ${agent.name}, creating response request" }
        
        // Create the response request using the agent's configuration
        val modelSettings = resolveModelSettings(agent)
        val responseRequest =
            CreateResponseRequest(
                model = modelSettings.qualifiedModelName,
                input = buildAgentMessages(request),
                instructions = agent.systemPrompt,
                tools = agent.tools,
                temperature = agent.temperature,
                stream = false, // Non-streaming request
            )
        
        // Use the facade service to process the request
        val result =
            responseFacadeService.processResponse(
                ResponseProcessingInput(
                    request = responseRequest,
                    headers = mapOf("Authorization" to "Bearer ${modelSettings.apiKey}"),
                ),
            )
        
        // Extract the response content
        return when (result) {
            is ResponseProcessingResult.NonStreaming -> {
                val content = extractContentFromResult(result.response)
                log.debug { "Successfully processed ask agent request, response length: ${content.length}" }
                AskAgentResponse(content = content)
            }
            is ResponseProcessingResult.Streaming -> {
                // This should never happen since we set stream = false
                log.error { "Unexpected streaming result from facade service when stream=false was requested" }
                throw AgentRunException("Expected non-streaming response but received streaming result")
            }
            else -> throw AgentRunException("unexpected response received from agent.")
        }
    }

    suspend fun askAgentStream(request: AskAgentRequest): Flow<ServerSentEvent<String>> {
        TODO("to be implemented")
    }

    /**
     * Builds the message list for the agent request, including context if provided.
     */
    private fun buildAgentMessages(request: AskAgentRequest): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()
        
        // Add context as a system message if provided
        request.context?.let {
            messages.add(
                mapOf(
                    "role" to "user",
                    "content" to "Context for the query: ${request.context}",
                ),
            )
        }

        // Add the user's query
        messages.add(
            mapOf(
                "role" to "user", 
                "content" to request.query,
            ),
        )
        
        return messages
    }

    /**
     * Extracts the content from the response result (which could be a Response object or formatted JSON).
     */
    private fun extractContentFromResult(response: Response): String {
        val modelOutputs = ResponsesUtils.toNormalizedOutput(response).firstOrNull()
        return modelOutputs?.let {
            modelOutputs.messages.firstOrNull()?.let {
                if (it.toolCalls.isNotEmpty()) {
                    throw AgentRunException("Tool call response is not expected. As external tool can't be executed.")
                }
                it.content
            }
        } ?: throw AgentRunException("No output from agent.")
    }

    private fun resolveModelSettings(agent: PlatformAgent): ModelSettings {
        if (modelSettings.settingsType == SystemSettingsType.RUNTIME) throw AgentRunException("Model and api key not available to run agent.")
        return modelSettings
    }
}

data class AskAgentRequest(
    val agentName: String = "",
    val query: String,
    val context: String? = null,
)

data class AskAgentResponse(
    val content: String,
)

class AgentRunException(
    message: String,
) : Exception(message)
