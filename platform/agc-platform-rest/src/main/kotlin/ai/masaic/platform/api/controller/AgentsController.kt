package ai.masaic.platform.api.controller

import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.platform.api.model.PlatformAgent
import ai.masaic.platform.api.service.*
import kotlinx.coroutines.flow.Flow
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/v1")
@CrossOrigin("*")
class AgentsController(
    private val agentService: AgentService,
    private val agentBuilderChatService: AgentBuilderChatService,
    private val askAgentService: AskAgentService,
) {
    @GetMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getAgent(
        @PathVariable agentName: String,
    ): ResponseEntity<PlatformAgent> {
        val agent =
            agentService.getAgent(agentName)
                ?: throw ResponseProcessingException("Agent: $agentName is not found.")
        
        return ResponseEntity.ok(agent.copy(name = PlatformAgent.presentableName(agent.name)))
    }

    @PostMapping("/agents", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun saveAgent(
        @RequestBody agent: PlatformAgent,
    ): ResponseEntity<PlatformAgent> {
        agentService.saveAgent(agent, false)
        return ResponseEntity.created(URI.create("/agents/${agent.name}")).build()
    }

    @PutMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun updateAgent(
        @PathVariable agentName: String,
        @RequestBody agent: PlatformAgent,
    ): ResponseEntity<PlatformAgent> {
        agentService.saveAgent(agent, true)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/agents", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun listAgents(): ResponseEntity<List<PlatformAgent>> = ResponseEntity.ok(agentService.getAllAgents())

    @DeleteMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun deleteAgent(
        @PathVariable agentName: String,
    ): ResponseEntity<Unit> {
        val deleted = agentService.deleteAgent(agentName)
        return if (deleted) {
            ResponseEntity.ok().build()
        } else {
            throw ResponseProcessingException("Agent: $agentName is not found.")
        }
    }

    @PostMapping("/agents/agent-builder/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    suspend fun chatWithAgentBuilder(
        @RequestBody agentBuilderChatRequest: AgentBuilderChatRequest,
        @RequestHeader("Authorization") authBearerToken: String,
    ): ResponseEntity<Flow<ServerSentEvent<*>>> =
        ResponseEntity
            .ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(
                agentBuilderChatService
                    .chat(agentBuilderChatRequest, authBearerToken),
            )

    @PostMapping("/agents/{agentName}/ask", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun askAgent(
        @PathVariable agentName: String,
        @RequestBody request: AskAgentRequest,
    ): ResponseEntity<AskAgentResponse> =
        ResponseEntity
            .ok()
            .body(
                askAgentService.askAgent(request = request.copy(agentName = agentName)),
            )
}
