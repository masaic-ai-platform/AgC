package ai.masaic.openresponses.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ServerConfiguration {
    @Bean
    fun deploymentSettings(): DeploymentSettings = DeploymentSettings(System.getenv("OPENAI_BASE_URL"))
}

data class DeploymentSettings(
    val openAiBaseUrl: String? = null,
)
