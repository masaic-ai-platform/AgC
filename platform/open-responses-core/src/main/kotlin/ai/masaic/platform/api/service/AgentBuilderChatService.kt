package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.controller.ResponseController
import ai.masaic.openresponses.api.model.CreateResponseRequest
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.platform.api.model.PlatformAgent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.flow.Flow
import org.springframework.http.codec.ServerSentEvent
import org.springframework.util.MultiValueMap

@Suppress("UNCHECKED_CAST")
class AgentBuilderChatService(private val responseController: ResponseController, private val agentService: AgentService) {
    private val mapper = jacksonObjectMapper()
    suspend fun chat(request: AgentBuilderChatRequest, authHeader: String): Flow<ServerSentEvent<String>> {
        var updatedRequest = request.responsesRequest
        if(request.modifyAgent && request.responsesRequest.previousResponseId == null) {
            val agentName = request.modifiedAgentName ?: throw ResponseProcessingException("field modifiedAgentName is mandatory for Agent modification")
            updatedRequest = addUserMessage(request.modifiedAgentName, request.responsesRequest)
        }

        val response = responseController.createResponse(updatedRequest, MultiValueMap.fromMultiValue(mapOf("Authorization" to listOf(authHeader))), MultiValueMap.fromMultiValue(mapOf("empty" to listOf(""))))
        return response.body as Flow<ServerSentEvent<String>>
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
                    "text" to "Existing definition of agent: $agentName\n" +
                            mapper.writeValueAsString(platformAgent)
                )
            )
        )
    }
}

data class AgentBuilderChatRequest(val modifyAgent: Boolean = false, val modifiedAgentName: String ?= null, val responsesRequest: CreateResponseRequest)
