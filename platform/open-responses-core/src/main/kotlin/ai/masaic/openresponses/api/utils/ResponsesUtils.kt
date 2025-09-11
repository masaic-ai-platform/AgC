package ai.masaic.openresponses.api.utils

import ai.masaic.openresponses.api.support.service.TelemetryService.*
import com.openai.models.responses.Response

class ResponsesUtils {
    companion object {
        fun toNormalizedOutput(
            response: Response,
            captureMessageContent: Boolean = true,
        ): List<ModelOutput> =
            response.output().mapIndexed { index, output ->
                when {
                    output.isMessage() -> {
                        val content =
                            if (captureMessageContent) {
                                output.asMessage().content().joinToString("\n") {
                                    if (it.isOutputText()) it.asOutputText().text() else "output text not found"
                                }
                            } else {
                                null
                            }
                        ModelOutput(
                            messages = listOf(NormalizedMessage("assistant", content)),
                            finishReason = "stop",
                            usage = null,
                            index = index,
                        )
                    }

                    output.isFunctionCall() -> {
                        val toolCall = output.asFunctionCall()
                        val toolCalls =
                            listOf(
                                NormalizedToolCall(
                                    id = toolCall.id().get(),
                                    name = toolCall.name(),
                                    arguments = messageContent(toolCall.arguments(), captureMessageContent),
                                ),
                            )
                        ModelOutput(
                            messages = listOf(NormalizedMessage("assistant", null, toolCalls)),
                            finishReason = "tool_calls",
                            usage = null,
                            index = index,
                        )
                    }

                    else ->
                        ModelOutput(
                            messages = emptyList(),
                            finishReason = null,
                            usage = null,
                            index = index,
                        )
                }
            }

        private fun messageContent(
            content: String,
            captureMessageContent: Boolean,
        ) = if (captureMessageContent) content else ""
    }
}

data class NormalizedMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<NormalizedToolCall> = emptyList(),
)

data class NormalizedToolCall(
    val id: String,
    val name: String,
    val arguments: String?,
)

data class ModelOutput(
    val messages: List<NormalizedMessage>,
    val finishReason: String?,
    val usage: Usage?,
    val index: Int = 0,
)

data class Usage(
    val inputTokens: Long?,
    val outputTokens: Long?,
)
