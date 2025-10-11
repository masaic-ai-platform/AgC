package ai.masaic.platform.api.config

import ai.masaic.openresponses.tool.PlugableToolAdapter
import ai.masaic.platform.api.tools.SimpleMultiPlugAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class PlatformPlugableToolConfig {
    @Bean
    @Primary
    @ConditionalOnProperty(name = ["platform.deployment.multiplug.enabled"], havingValue = "true", matchIfMissing = true)
    fun simpleMultiplugAdapter(adapters: List<PlugableToolAdapter>) = SimpleMultiPlugAdapter(adapters)
}
