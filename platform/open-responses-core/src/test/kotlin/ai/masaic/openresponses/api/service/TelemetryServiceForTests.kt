package ai.masaic.openresponses.api.service

import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.openresponses.api.support.service.TelemetryService
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.Response
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span

class TelemetryServiceForTests(
    private val observationRegistry: ObservationRegistry,
    openTelemetry: OpenTelemetry,
    meterRegistry: MeterRegistry,
) : TelemetryService(observationRegistry, openTelemetry, meterRegistry) {
    override suspend fun emitModelInputEventsForOtelSpan(
        span: Span,
        inputParams: ChatCompletionCreateParams,
        metadata: InstrumentationMetadataInput,
    ) {
        println("telemetry service for tests....")
    }

    override fun emitModelOutputEventsForOtelSpan(
        span: Span,
        response: Response,
        metadata: InstrumentationMetadataInput,
    ) {
        println("telemetry service for tests....")
    }
}
