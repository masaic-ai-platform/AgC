package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.service.search.VectorStoreService
import ai.masaic.openresponses.api.support.service.TelemetryService
import ai.masaic.openresponses.api.utils.PayloadFormatter
import ai.masaic.openresponses.api.validation.RequestValidator
import ai.masaic.openresponses.tool.ToolService
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.OpenTelemetry
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ServerConfiguration {
    @Bean
    fun deploymentSettings(): DeploymentSettings = DeploymentSettings(System.getenv("OPENAI_BASE_URL"))

    @Bean
    @ConditionalOnMissingBean
    fun telemetryService(
        observationRegistry: ObservationRegistry,
        openTelemetry: OpenTelemetry,
        meterRegistry: MeterRegistry,
    ) = TelemetryService(observationRegistry, openTelemetry, meterRegistry)

    @Bean
    @ConditionalOnMissingBean
    fun payloadFormatter(
        toolService: ToolService,
        mapper: ObjectMapper,
    ) = PayloadFormatter(toolService, mapper)

    @Bean
    @ConditionalOnMissingBean
    fun requestValidator(
        vectorStoreService: VectorStoreService,
        responseStore: ResponseStore,
        deploymentSettings: DeploymentSettings,
    ) = RequestValidator(vectorStoreService, responseStore, deploymentSettings)
}

data class DeploymentSettings(
    val openAiBaseUrl: String? = null,
)
