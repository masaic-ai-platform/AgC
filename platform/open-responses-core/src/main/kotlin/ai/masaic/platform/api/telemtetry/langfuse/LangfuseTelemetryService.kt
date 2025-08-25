package ai.masaic.platform.api.telemtetry.langfuse

import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.platform.api.telemtetry.PlatformTelemetryService
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.Response
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry

class LangfuseTelemetryService(
    private val observationRegistry: ObservationRegistry,
    meterRegistry: MeterRegistry,
) : PlatformTelemetryService(observationRegistry, meterRegistry) {
    override suspend fun emitModelInputEvents(
        observation: Observation,
        inputParams: ChatCompletionCreateParams,
        metadata: InstrumentationMetadataInput,
    ) {
        val context = TelemetryContext(metadata, captureMessageContent)
        val normalizedMessages = inputParams.toNormalizedMessages()
        val events = extractMessageEvents(normalizedMessages, context)

        events.forEachIndexed { index, event ->
            observation.event(
                Observation.Event.of(event.name, mapper.writeValueAsString(event)),
            )

            observation.highCardinalityKeyValue("gen_ai.prompt.$index.role", event.role)
            when (val payload = event.payload) {
                is TelemetryPayload.MessagePayload -> {
                    observation.highCardinalityKeyValue("gen_ai.prompt.$index.content", payload.content ?: "")
                }
                is TelemetryPayload.ToolCallsPayload -> {
                    observation.highCardinalityKeyValue("gen_ai.prompt.$index.content", mapper.writeValueAsString(payload.toolCalls))
                }
                else -> throw IllegalStateException("Unexpected event payload type found.")
            }
        }
    }

    override fun emitModelOutputEvents(
        observation: Observation,
        response: Response,
        metadata: InstrumentationMetadataInput,
    ) {
        val context = TelemetryContext(metadata, captureMessageContent)
        val normalizedOutputs = response.toNormalizedOutput()
        val events = extractOutputEvents(normalizedOutputs, context)

        events.forEachIndexed { index, event ->
            observation.event(
                Observation.Event.of(event.name, mapper.writeValueAsString(event)),
            )

            observation.highCardinalityKeyValue("gen_ai.completion.$index.role", event.role)
            when (val payload = event.payload) {
                is TelemetryPayload.ChoicePayload -> {
                    observation.highCardinalityKeyValue("gen_ai.completion.$index.content", payload.content ?: "")
                }
                is TelemetryPayload.ToolChoicePayload -> {
                    observation.highCardinalityKeyValue("gen_ai.completion.$index.content", mapper.writeValueAsString(payload.toolCalls))
                }
                else -> throw IllegalStateException("Unexpected event payload type found.")
            }
        }
    }
}
