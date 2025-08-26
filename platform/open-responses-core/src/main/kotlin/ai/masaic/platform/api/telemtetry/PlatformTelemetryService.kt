package ai.masaic.platform.api.telemtetry

import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.openresponses.api.support.service.GenAIObsAttributes
import ai.masaic.openresponses.api.support.service.TelemetryService
import ai.masaic.platform.api.user.CurrentUserProvider
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.OpenTelemetry

class PlatformTelemetryService(
    private val observationRegistry: ObservationRegistry,
    openTelemetry: OpenTelemetry,
    meterRegistry: MeterRegistry,
) : TelemetryService(observationRegistry, openTelemetry, meterRegistry)
