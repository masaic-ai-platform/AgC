package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.model.CompletedEventData
import ai.masaic.openresponses.api.model.CreateResponseRequest
import ai.masaic.openresponses.api.model.EventData
import ai.masaic.openresponses.api.service.ResponseFacadeService
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.api.service.ResponseProcessingInput
import ai.masaic.openresponses.api.service.ResponseProcessingResult
import ai.masaic.platform.api.model.PlatformAgent
import ai.masaic.platform.api.tools.PlatformToolsNames
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import java.util.UUID

class AgentBuilderChatService(
    private val responseFacadeService: ResponseFacadeService,
    private val agentService: AgentService,
) {
    private val mapper = jacksonObjectMapper()
    private val log = KotlinLogging.logger { }

    /**
     * Handles agent builder chat requests.
     * This method always returns a streaming response since agent conversations require real-time interaction.
     */
    suspend fun chat(
        request: AgentBuilderChatRequest,
        authHeader: String,
    ): Flow<ServerSentEvent<*>> {
        var updatedRequest = request.responsesRequest
        if (request.modifyAgent && request.responsesRequest.previousResponseId == null) {
            val agentName = request.modifiedAgentName ?: throw ResponseProcessingException("field modifiedAgentName is mandatory for Agent modification")
            updatedRequest = addUserMessage(request.modifiedAgentName, request.responsesRequest)
        }

        // Ensure the request is set to streaming since AgentBuilderChatService always returns a stream
        val streamingRequest = updatedRequest.copy(stream = true)
        
        // Use the simplified facade service wrapper
        val result =
            responseFacadeService.processResponse(
                ResponseProcessingInput(
                    request = streamingRequest,
                    headers = mapOf("Authorization" to authHeader),
                ),
            )

        // Extract the streaming flow (AgentBuilderChatService always works with streams)
        val upStream =
            when (result) {
                is ResponseProcessingResult.Streaming -> result.flow
                is ResponseProcessingResult.NonStreaming -> {
                    // This should never happen since we explicitly set stream = true
                    log.error { "Unexpected non-streaming result from facade service when stream=true was requested" }
                    throw ResponseProcessingException("Expected streaming response but received non-streaming result")
                }
            }
        var saveAgentResponse: SaveAgentResponse? = null
        return flow {
            upStream.collect { event ->
                val receivedEventName = event.event()?.trim() ?: ""
                emit(event)
                if (!request.modifyAgent && receivedEventName.startsWith("response.created")) {
                    emit(event("response.agent.creation.in_progress"))
                }

                if (!request.modifyAgent && receivedEventName.startsWith("response.completed")) {
                    if (saveAgentResponse?.isSuccess == true) {
                        emit(event("response.agent.updated", eventData = SaveAgentEventData(itemId = UUID.randomUUID().toString(), outputIndex = "0", type = "response.agent.updated", agentName = saveAgentResponse?.agentName ?: "not available")))
                    } else {
                        emit(event("response.agent.creation.paused"))
                    }
                }

                if (receivedEventName.startsWith("response.agc.${PlatformToolsNames.SAVE_AGENT_TOOL}.completed")) {
                    val toolCompletedEventData: CompletedEventData = mapper.readValue(event.data() as String)
                    saveAgentResponse = mapper.readValue(toolCompletedEventData.toolResult)

                    if (request.modifyAgent && saveAgentResponse?.isSuccess == true) {
                        emit(event("response.agent.updated", eventData = SaveAgentEventData(itemId = UUID.randomUUID().toString(), outputIndex = "0", type = "response.agent.updated", agentName = saveAgentResponse?.agentName ?: "not available")))
                    }
                }

                if (receivedEventName == "response.incomplete") {
                    log.error { "Response incomplete, ending stream, reason: ${event.data()}" }
                    emit(event("error"))
                }
            }
        }.catch { error ->
            log.error { "Exception while streaming, $error" }
            emit(event("error"))
        }
    }

    private suspend fun addUserMessage(
        agentName: String,
        responsesRequest: CreateResponseRequest,
    ): CreateResponseRequest {
        val agentMeta = agentService.getAgent(agentName) ?: throw ResponseProcessingException("agent $agentName not found.")
        val finalMessages = (responsesRequest.input as List<*>).toMutableList() + modifyAgentMessage(agentName, agentMeta)
        return responsesRequest.copy(input = finalMessages)
    }

    private fun modifyAgentMessage(
        agentName: String,
        platformAgent: PlatformAgent,
    ): Map<String, Any> =
        mapOf(
            "role" to "user",
            "content" to
                listOf(
                    mapOf(
                        "type" to "input_text",
                        "text" to "Modify the agent with name: agentName='$agentName'\nExisting definition of the agent is below:\n" +
                            mapper.writeValueAsString(platformAgent),
                    ),
                ),
        )

    private fun event(
        eventName: String,
        eventData: EventData? = null,
    ) = ServerSentEvent
        .builder<String>()
        .event(eventName)
        .data(
            " " +
                mapper.writeValueAsString(
                    eventData?.let { eventData } ?: EventData(itemId = UUID.randomUUID().toString(), outputIndex = "0", type = eventName),
                ),
        ).build()
}

data class AgentBuilderChatRequest(
    val modifyAgent: Boolean = false,
    val modifiedAgentName: String? = null,
    val responsesRequest: CreateResponseRequest,
)

data class SaveAgentEventData(
    @JsonProperty("item_id")
    override val itemId: String,
    @JsonProperty("output_index")
    override val outputIndex: String,
    override val type: String,
    val agentName: String,
) : EventData(itemId, outputIndex, type)
