package ai.masaic.platform.api.telemtetry

import ai.masaic.openresponses.api.support.service.TelemetryService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.OpenTelemetry

class PlatformTelemetryService(
    private val observationRegistry: ObservationRegistry,
    openTelemetry: OpenTelemetry,
    meterRegistry: MeterRegistry,
) : TelemetryService(observationRegistry, openTelemetry, meterRegistry)
