package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.controller.ResponseController
import ai.masaic.openresponses.api.model.CreateResponseRequest
import ai.masaic.openresponses.api.model.CompletedEventData
import ai.masaic.openresponses.api.model.EventData
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.platform.api.model.PlatformAgent
import ai.masaic.platform.api.tools.PlatformToolsNames
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import org.springframework.util.MultiValueMap
import java.util.UUID

@Suppress("UNCHECKED_CAST")
class AgentBuilderChatService(private val responseController: ResponseController, private val agentService: AgentService) {
    private val mapper = jacksonObjectMapper()
    private val log = KotlinLogging.logger {  }

    suspend fun chat(request: AgentBuilderChatRequest, authHeader: String): Flow<ServerSentEvent<*>> {
        var updatedRequest = request.responsesRequest
        if(request.modifyAgent && request.responsesRequest.previousResponseId == null) {
            val agentName = request.modifiedAgentName ?: throw ResponseProcessingException("field modifiedAgentName is mandatory for Agent modification")
            updatedRequest = addUserMessage(request.modifiedAgentName, request.responsesRequest)
        }

        val response = responseController.createResponse(updatedRequest, MultiValueMap.fromMultiValue(mapOf("Authorization" to listOf(authHeader))), MultiValueMap.fromMultiValue(mapOf("empty" to listOf(""))))

        val upStream = response.body as Flow<ServerSentEvent<*>>
        var isSaveAgentSuccessful = false
        return flow {
            upStream.collect { event ->
                val receivedEventName = event.event()?.trim() ?: ""
                emit(event)
                if(!request.modifyAgent && receivedEventName.startsWith("response.created")) {
                    emit(event("response.agent.creation.in_progress"))
                }

                if(!request.modifyAgent && receivedEventName.startsWith("response.completed") && !isSaveAgentSuccessful) {
                    emit(event("response.agent.creation.paused"))
                }

                if(receivedEventName.startsWith("response.agc.${PlatformToolsNames.SAVE_AGENT_TOOL}.completed")) {
                    val toolCompletedEventData: CompletedEventData = mapper.readValue(event.data() as String)
                    val saveAgentResponse: SaveAgentResponse = mapper.readValue(toolCompletedEventData.toolResult)
                    if(saveAgentResponse.isSuccess) {
                        emit(event("response.agent.updated",eventData = SaveAgentEventData(itemId = UUID.randomUUID().toString(), outputIndex = "0", type = "response.agent.updated", agentName = saveAgentResponse.agentName)))
                        isSaveAgentSuccessful = true
                    }
                }
            }
        }
    }

    private suspend fun addUserMessage(agentName: String, responsesRequest: CreateResponseRequest): CreateResponseRequest{
        val agentMeta = agentService.getAgent(agentName) ?: throw ResponseProcessingException("agent $agentName not found.")
        val finalMessages = (responsesRequest.input as List<*>).toMutableList() + modifyAgentMessage(agentName, agentMeta)
        return responsesRequest.copy(input = finalMessages)
    }

    private fun modifyAgentMessage(agentName: String, platformAgent: PlatformAgent): Map<String, Any> {
        return mapOf(
            "role" to "user",
            "content" to listOf(
                mapOf(
                    "type" to "input_text",
                    "text" to "Modify the agent with name: agentName='$agentName'\nExisting definition of the agent is below:\n" +
                            mapper.writeValueAsString(platformAgent)
                )
            )
        )
    }

    private fun event(eventName: String, eventData: EventData ?= null) = ServerSentEvent
        .builder<String>()
        .event(eventName)
        .data(" " + mapper.writeValueAsString(
            eventData?.let { eventData } ?: EventData(itemId = UUID.randomUUID().toString(), outputIndex = "0", type = eventName)
        )).build()
}

data class AgentBuilderChatRequest(val modifyAgent: Boolean = false, val modifiedAgentName: String ?= null, val responsesRequest: CreateResponseRequest)

data class SaveAgentEventData(
    @JsonProperty("item_id")
    override val itemId: String,

    @JsonProperty("output_index")
    override val outputIndex: String,

    override val type: String,

    val agentName: String
) : EventData(itemId, outputIndex, type)
