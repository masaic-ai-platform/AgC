package ai.masaic.platform.api.telemtetry

import ai.masaic.openresponses.api.support.service.TelemetryService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry

class PlatformTelemetryService(
    private val observationRegistry: ObservationRegistry,
    meterRegistry: MeterRegistry,
) : TelemetryService(observationRegistry, meterRegistry)
