package ai.masaic.platform.api.controller

import ai.masaic.openresponses.api.exception.AgentNotFoundException
import ai.masaic.openresponses.api.exception.ResourceNotFoundException
import ai.masaic.openresponses.api.model.*
import ai.masaic.platform.api.tools.*
import ai.masaic.platform.api.service.AgentService
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.http.HttpStatus
import org.checkerframework.checker.units.qual.Temperature
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.Instant

@Profile("platform")
@RestController
@RequestMapping("/v1")
@CrossOrigin("*")
class AgentsController(
    private val agentService: AgentService
) {
    @GetMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getAgent(
        @PathVariable agentName: String,
    ): ResponseEntity<PlatformAgent> {
        val agent = agentService.getAgent(agentName)
            ?: throw AgentNotFoundException("Agent: $agentName is not supported.")
        
        return ResponseEntity.ok(agent)
    }

    @PostMapping("/agents", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun saveAgent(
        @RequestBody agent: PlatformAgent
    ): ResponseEntity<PlatformAgent> {
        agentService.saveAgent(agent)
        return ResponseEntity.created(URI.create("/agents/${agent.name}")).build()
    }

    @GetMapping("/agents", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun listAgents(): ResponseEntity<List<PlatformAgent>> {
        // Only returns persisted agents (not SYSTEM agents)
        val agents = agentService.getAllAgents()
        return ResponseEntity.ok(agents)
    }

    @DeleteMapping("/agents/{agentName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun deleteAgent(
        @PathVariable agentName: String
    ): ResponseEntity<Unit> {
        val deleted = agentService.deleteAgent(agentName)
        return if (deleted) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}

data class PlatformAgent(
    val model: String ?= null,
    val name: String,
    val description: String,
    val greetingMessage: String ?= null,
    val systemPrompt: String,
    val userMessage: String? = null,
    val tools: List<Tool> = emptyList(),
    val formatType: String = "text",
    val temperature: Double = 1.0,
    @JsonProperty("max_output_tokens") val maxTokenOutput: Int = 2048,
    @JsonProperty("top_p") val topP: Double = 1.0,
    val store: Boolean = true,
    val stream: Boolean = true,
    @JsonIgnore
    val kind: AgentClass = AgentClass(AgentClass.OTHER)
)

data class PlatformAgentMeta(
    val name: String,
    val description: String,
    val greetingMessage: String ?= null,
    val systemPrompt: String,
    val userMessage: String? = null,
    val toolMeta: ToolMeta = ToolMeta(),
    val formatType: String = "text",
    val temperature: Double = 1.0,
    @JsonProperty("max_output_tokens") val maxTokenOutput: Int = 2048,
    @JsonProperty("top_p") val topP: Double = 1.0,
    val store: Boolean = true,
    val stream: Boolean = true,
    val modelInfo: ModelInfoMeta ?= null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    @JsonIgnore
    val kind: AgentClass = AgentClass(AgentClass.OTHER)
)

data class ToolMeta(
    val mcpTools: List<McpToolMeta> = emptyList(),
    val fileSearchTools: List<FileSearchToolMeta> = emptyList(),
    val agenticSearchTools: List<AgenticSearchToolMeta> = emptyList(),
    val pyFunTools: List<PyFunToolMeta> = emptyList()
)

data class McpToolMeta(
    @JsonProperty("server_label")
    val serverLabel: String,
    @JsonProperty("server_url")
    val serverUrl: String,
    @JsonProperty("allowed_tools")
    val allowedTools: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
)

data class FileSearchToolMeta(
    val filters: Any? = null,
    @JsonProperty("max_num_results")
    val maxNumResults: Int = 20,
    @JsonProperty("ranking_options")
    val rankingOptions: RankingOptionsMeta? = null,
    @JsonProperty("vector_store_ids")
    val vectorStoreIds: List<String>,
    val modelInfo: ModelInfoMeta,
)

data class AgenticSearchToolMeta(
    val filters: Any? = null,
    @JsonProperty("max_num_results")
    val maxNumResults: Int = 20,
    @JsonProperty("vector_store_ids")
    val vectorStoreIds: List<String>? = null,
    @JsonProperty("max_iterations")
    val maxIterations: Int = 5,
    @JsonProperty("enable_presence_penalty_tuning")
    val enablePresencePenaltyTuning: Boolean? = null,
    @JsonProperty("enable_frequency_penalty_tuning")
    val enableFrequencyPenaltyTuning: Boolean? = null,
    @JsonProperty("enable_temperature_tuning")
    val enableTemperatureTuning: Boolean? = null,
    @JsonProperty("enable_top_p_tuning")
    val enableTopPTuning: Boolean? = null,
    val modelInfo: ModelInfoMeta,
)

data class ModelInfoMeta(val name: String ?= null)

data class PyFunToolMeta(
    val id: String
)

data class RankingOptionsMeta(
    val ranker: String = "auto",
    @JsonProperty("score_threshold")
    val scoreThreshold: Double = 0.0,
)

data class AgentClass(
    val kind: String,
) {
    companion object {
        const val SYSTEM = "system"
        const val OTHER = "other"
    }
}
