package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.tool.NoOpPlugableToolAdapter
import ai.masaic.openresponses.tool.PlugableToolAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PlugableToolConfig {
    @Bean
    @ConditionalOnMissingBean(PlugableToolAdapter::class)
    fun noOpPlugableToolAdapter(): PlugableToolAdapter = NoOpPlugableToolAdapter()
}
