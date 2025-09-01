package ai.masaic.openresponses.api.model

import com.fasterxml.jackson.annotation.JsonProperty

open class EventData(
    @JsonProperty("item_id")
    open val itemId: String,
    @JsonProperty("output_index")
    open val outputIndex: String,
    open val type: String,
)

data class ExecutingEventData(
    @JsonProperty("item_id")
    override val itemId: String,
    @JsonProperty("output_index")
    override val outputIndex: String,
    override val type: String,
) : EventData(itemId, outputIndex, type)

data class InProgressEventData(
    @JsonProperty("item_id")
    override val itemId: String,
    @JsonProperty("output_index")
    override val outputIndex: String,
    override val type: String,
    @JsonProperty("tool_args")
    val toolArgs: String,
) : EventData(itemId, outputIndex, type)

data class CompletedEventData(
    @JsonProperty("item_id")
    override val itemId: String,
    @JsonProperty("output_index")
    override val outputIndex: String,
    override val type: String,
    @JsonProperty("tool_result")
    val toolResult: String,
) : EventData(itemId, outputIndex, type)

data class ErrorEventData(
    @JsonProperty("item_id")
    override val itemId: String,
    @JsonProperty("output_index")
    override val outputIndex: String,
    override val type: String,
    val error: String,
) : EventData(itemId, outputIndex, type)
