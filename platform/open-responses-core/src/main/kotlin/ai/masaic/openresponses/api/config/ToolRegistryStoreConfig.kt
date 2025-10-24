package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.tool.mcp.CaffeineToolRegistryStorage
import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ToolRegistryStoreConfig {
    @Bean
    @ConditionalOnProperty(name = ["open-responses.tool.registry.store.type"], havingValue = "in-memory", matchIfMissing = true)
    @ConditionalOnMissingBean(ToolRegistryStorage::class)
    fun caffeineToolRegistryStorage(caffeineCacheConfig: ToolsCaffeineCacheConfig) = CaffeineToolRegistryStorage(caffeineCacheConfig)
}

@ConfigurationProperties("open-responses.tool.registry.store.caffeine")
data class ToolsCaffeineCacheConfig(
    val maxSize: Long = 500,
    val ttlMinutes: Long = 10,
)
