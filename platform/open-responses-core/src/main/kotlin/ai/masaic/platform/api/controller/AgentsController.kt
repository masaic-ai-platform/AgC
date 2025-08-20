package ai.masaic.platform.api.controller

import ai.masaic.openresponses.api.exception.AgentNotFoundException
import ai.masaic.openresponses.api.model.*
import ai.masaic.platform.api.model.PlatformAgent
import ai.masaic.platform.api.service.AgentService
import ai.masaic.platform.api.tools.*
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@Profile("platform")
@RestController
@RequestMapping("/v1")
@CrossOrigin("*")
class AgentsController(
    private val agentService: AgentService,
) {
    @GetMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getAgent(
        @PathVariable agentName: String,
    ): ResponseEntity<PlatformAgent> {
        val agent =
            agentService.getAgent(agentName.lowercase())
                ?: throw AgentNotFoundException("Agent: $agentName is not found.")
        
        return ResponseEntity.ok(agent.copy(name = agent.presentableName()))
    }

    @PostMapping("/agents", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun saveAgent(
        @RequestBody agent: PlatformAgent,
    ): ResponseEntity<PlatformAgent> {
        agentService.saveAgent(agent.copy(name = agent.name.lowercase()), false)
        return ResponseEntity.created(URI.create("/agents/${agent.name}")).build()
    }

    @PutMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun updateAgent(
        @PathVariable agentName: String,
        @RequestBody agent: PlatformAgent,
    ): ResponseEntity<PlatformAgent> {
        agentService.saveAgent(agent.copy(name = agent.name.lowercase()), true)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/agents", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun listAgents(): ResponseEntity<List<PlatformAgent>> {
        val agents = agentService.getAllAgents().map { it.copy(name = it.presentableName()) }
        return ResponseEntity.ok(agents)
    }

    @DeleteMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun deleteAgent(
        @PathVariable agentName: String,
    ): ResponseEntity<Unit> {
        val deleted = agentService.deleteAgent(agentName.lowercase())
        return if (deleted) {
            ResponseEntity.ok().build()
        } else {
            throw AgentNotFoundException("Agent: $agentName is not found.")
        }
    }
}
