package ai.masaic.platform.api.telemtetry.langfuse

import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.openresponses.api.support.service.GenAIObsAttributes
import ai.masaic.platform.api.telemtetry.PlatformTelemetryService
import ai.masaic.platform.api.user.CurrentUserProvider
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.Response
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context

class LangfuseTelemetryService(
    private val observationRegistry: ObservationRegistry,
    openTelemetry: OpenTelemetry,
    meterRegistry: MeterRegistry,
) : PlatformTelemetryService(observationRegistry, openTelemetry, meterRegistry) {

//    override suspend fun startObservation(
//        operationName: String,
//        modelName: String,
//        parentObservation: Observation?,
//    ): Observation {
////        val observation = super.startObservation(operationName, modelName, parentObservation)
//        val observation = Observation.createNotStarted("$operationName $modelName", observationRegistry)
//        parentObservation?.let { observation.parentObservation(it) }
//        observation.highCardinalityKeyValue(GenAIObsAttributes.SPAN_KIND, "client")
//        observation.start()
//        CurrentUserProvider.userId()?.let { observation.highCardinalityKeyValue("user.id", it) }
//        CurrentUserProvider.sessionId()?.let { observation.highCardinalityKeyValue("session.id", it) }
//        return observation
//    }

    override suspend fun startOtelSpan(
        operationName: String,
        modelName: String,
        parentSpan: Span?,
    ): Span {
        val span = super.startOtelSpan(operationName, modelName, parentSpan)
        CurrentUserProvider.userId()?.let { span.setAttribute("user.id", it) }
        CurrentUserProvider.sessionId()?.let { span.setAttribute("session.id", it) }
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
//            span.addEvent(event.name, Attributes.builder().put(event.name, mapper.writeValueAsString(event.payload)).build())

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

//    override suspend fun emitModelInputEvents(
//        observation: Observation,
//        inputParams: ChatCompletionCreateParams,
//        metadata: InstrumentationMetadataInput,
//    ) {
//        val context = TelemetryContext(metadata, captureMessageContent)
//        val normalizedMessages = inputParams.toNormalizedMessages()
//        val events = extractMessageEvents(normalizedMessages, context)
//
//        events.forEachIndexed { index, event ->
//            observation.event(
//                Observation.Event.of(event.name, mapper.writeValueAsString(event)),
//            )
//
//            observation.highCardinalityKeyValue("gen_ai.prompt.$index.role", event.role)
//            when (val payload = event.payload) {
//                is TelemetryPayload.MessagePayload -> {
//                    observation.highCardinalityKeyValue("gen_ai.prompt.$index.content", payload.content ?: "")
//                }
//                is TelemetryPayload.ToolCallsPayload -> {
//                    observation.highCardinalityKeyValue("gen_ai.prompt.$index.content", mapper.writeValueAsString(payload.toolCalls))
//                }
//                else -> throw IllegalStateException("Unexpected event payload type found.")
//            }
//        }
//    }

    override fun emitModelOutputEventsForOtelSpan(
        span: Span,
        response: Response,
        metadata: InstrumentationMetadataInput,
    ) {
        val context = TelemetryContext(metadata, captureMessageContent)
        val normalizedOutputs = response.toNormalizedOutput()
        val events = extractOutputEvents(normalizedOutputs, context)

        events.forEachIndexed { index, event ->
//            span.addEvent(event.name, Attributes.builder().put(event.name, mapper.writeValueAsString(event.payload)).build())

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

//    override fun emitModelOutputEvents(
//        observation: Observation,
//        response: Response,
//        metadata: InstrumentationMetadataInput,
//    ) {
//        val context = TelemetryContext(metadata, captureMessageContent)
//        val normalizedOutputs = response.toNormalizedOutput()
//        val events = extractOutputEvents(normalizedOutputs, context)
//
//        events.forEachIndexed { index, event ->
//            observation.event(
//                Observation.Event.of(event.name, mapper.writeValueAsString(event)),
//            )
//
//            observation.highCardinalityKeyValue("gen_ai.completion.$index.role", event.role)
//            when (val payload = event.payload) {
//                is TelemetryPayload.ChoicePayload -> {
//                    observation.highCardinalityKeyValue("gen_ai.completion.$index.content", payload.content ?: "")
//                }
//                is TelemetryPayload.ToolChoicePayload -> {
//                    observation.highCardinalityKeyValue("gen_ai.completion.$index.content", mapper.writeValueAsString(payload.toolCalls))
//                }
//                else -> throw IllegalStateException("Unexpected event payload type found.")
//            }
//        }
//    }
}
