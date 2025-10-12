package ai.masaic.platform.api.telemtetry.langfuse

import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.openresponses.api.utils.AgCLoopContext
import ai.masaic.openresponses.api.utils.ResponsesUtils
import ai.masaic.platform.api.telemtetry.PlatformTelemetryService
import ai.masaic.platform.api.user.UserInfoProvider
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.Response
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import kotlin.jvm.optionals.getOrDefault

class LangfuseTelemetryService(
    private val observationRegistry: ObservationRegistry,
    openTelemetry: OpenTelemetry,
    meterRegistry: MeterRegistry,
) : PlatformTelemetryService(observationRegistry, openTelemetry, meterRegistry) {
    override suspend fun startOtelSpan(
        operationName: String,
        modelName: String,
        parentSpan: Span?,
    ): Span {
        val span = super.startOtelSpan(operationName, modelName, parentSpan)
        UserInfoProvider.userId()?.let { span.setAttribute("user.id", it) } ?: AgCLoopContext.userId()?.let { span.setAttribute("user.id", it) }
        UserInfoProvider.sessionId()?.let { span.setAttribute("session.id", it) } ?: AgCLoopContext.sessionId()?.let { span.setAttribute("session.id", it) }
        return span
    }

    override suspend fun emitModelInputEventsForOtelSpan(
        span: Span,
        inputParams: ChatCompletionCreateParams,
        metadata: InstrumentationMetadataInput,
    ) {
        val context = TelemetryContext(metadata, captureMessageContent)
        val normalizedMessages = inputParams.toNormalizedMessages()
        val events = extractMessageEvents(normalizedMessages, context)

        events.forEachIndexed { index, event ->
            span.setAttribute("gen_ai.prompt.$index.role", event.role)
            when (val payload = event.payload) {
                is TelemetryPayload.MessagePayload -> {
                    span.setAttribute("gen_ai.prompt.$index.content", payload.content ?: "")
                }
                is TelemetryPayload.ToolCallsPayload -> {
                    span.setAttribute("gen_ai.prompt.$index.content", mapper.writeValueAsString(payload.toolCalls))
                }
                else -> throw IllegalStateException("Unexpected event payload type found.")
            }
        }
    }

    override fun emitModelOutputEventsForOtelSpan(
        span: Span,
        response: Response,
        metadata: InstrumentationMetadataInput,
    ) {
        val context = TelemetryContext(metadata, captureMessageContent)
        val normalizedOutputs = ResponsesUtils.toNormalizedOutput(response)
        val events = extractOutputEvents(normalizedOutputs, context)

        events.forEachIndexed { index, event ->
            span.setAttribute("gen_ai.completion.$index.role", event.role)
            when (val payload = event.payload) {
                is TelemetryPayload.ChoicePayload -> {
                    span.setAttribute("gen_ai.completion.$index.content", payload.content ?: "")
                }
                is TelemetryPayload.ToolChoicePayload -> {
                    span.setAttribute("gen_ai.completion.$index.content", mapper.writeValueAsString(payload.toolCalls))
                }
                else -> throw IllegalStateException("Unexpected event payload type found.")
            }
        }
    }

    override fun emitModelOutputEventsForOtel(
        span: Span,
        chatCompletion: ChatCompletion,
        metadata: InstrumentationMetadataInput,
    ) {
        chatCompletion.choices().forEachIndexed { index, choice ->
            span.setAttribute("gen_ai.completion.$index.role", "assistant")
            val content = messageContent(choice.message().content().getOrDefault(""))
            if (content.isNotEmpty()) {
                span.setAttribute("gen_ai.completion.$index.content", content)
            }

            if (choice.finishReason().asString().lowercase() == "tool_calls") {
                val toolCalls =
                    choice.message().toolCalls().get().map { tool ->
                        val functionDetailsMap = mutableMapOf("name" to tool.function().name())
                        putIfNotEmpty(functionDetailsMap, "arguments", messageContent(tool.function().arguments()))
                        mapOf(
                            "id" to tool.id(),
                            "type" to "function",
                            "function" to functionDetailsMap,
                        )
                    }
                val tooCallMap =
                    mapOf(
                        "gen_ai.system" to metadata.genAISystem,
                        "finish_reason" to choice.finishReason().asString(),
                        "index" to choice.index().toString(),
                        "tool_calls" to toolCalls,
                    )

                span.setAttribute("gen_ai.completion.$index.content", mapper.writeValueAsString(toolCalls))
            }
        }
    }
}
