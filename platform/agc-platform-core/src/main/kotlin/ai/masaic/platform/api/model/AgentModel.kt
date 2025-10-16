package ai.masaic.platform.api.model

import ai.masaic.openresponses.api.model.Tool
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class PlatformAgent(
    val model: String? = null,
    val name: String,
    val description: String,
    val greetingMessage: String? = null,
    val systemPrompt: String,
    val userMessage: String? = null,
    val suggestedQueries: List<String> = emptyList(),
    val tools: List<Tool> = emptyList(),
    val formatType: String = "text",
    val temperature: Double = 1.0,
    @JsonProperty("max_output_tokens") val maxTokenOutput: Int = 2048,
    @JsonProperty("top_p") val topP: Double = 1.0,
    val store: Boolean = true,
    val stream: Boolean = true,
    @JsonIgnore
    val kind: AgentClass = AgentClass(AgentClass.OTHER),
) {
    companion object {
        fun presentableName(name: String) = name.replaceFirstChar { it.uppercase() }

        fun persistenceName(name: String) = name.replaceFirstChar { it.lowercase() }
    }
}

data class PlatformAgentMeta(
    @org.springframework.data.annotation.Id
    val name: String,
    val description: String,
    val greetingMessage: String? = null,
    val systemPrompt: String,
    val userMessage: String? = null,
    val suggestedQueries: List<String> = emptyList(),
    val toolMeta: ToolMeta = ToolMeta(),
    val formatType: String = "text",
    val temperature: Double = 1.0,
    @JsonProperty("max_output_tokens") val maxTokenOutput: Int = 2048,
    @JsonProperty("top_p") val topP: Double = 1.0,
    val store: Boolean = true,
    val stream: Boolean = true,
    val modelInfo: ModelInfoMeta? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    @JsonIgnore
    val kind: AgentClass = AgentClass(AgentClass.OTHER),
    @JsonProperty("access_control")
    val accessControlJson: String? = null,
)

data class ToolMeta(
    val mcpTools: List<McpToolMeta> = emptyList(),
    val fileSearchTools: List<FileSearchToolMeta> = emptyList(),
    val agenticSearchTools: List<AgenticSearchToolMeta> = emptyList(),
    val pyFunTools: List<PyFunToolMeta> = emptyList(),
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

data class ModelInfoMeta(
    val name: String? = null,
)

data class PyFunToolMeta(
    val id: String,
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
