package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.tool.mcp.InMemoryMcpServerInfoRegistryStorage
import ai.masaic.openresponses.tool.mcp.InMemoryToolRegistryStorage
import ai.masaic.openresponses.tool.mcp.McpServerInfoRegistryStorage
import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["open-responses.tool.store.type"], havingValue = "in-memory", matchIfMissing = true)
@EnableConfigurationProperties(ToolsCaffeineCacheConfig::class)
class ToolStoreConfig {
    @Bean
    @ConditionalOnMissingBean(ToolRegistryStorage::class)
    fun caffeineToolRegistryStorage(caffeineCacheConfig: ToolsCaffeineCacheConfig) = InMemoryToolRegistryStorage(caffeineCacheConfig)

    @Bean
    @ConditionalOnMissingBean(McpServerInfoRegistryStorage::class)
    fun caffeineMcpServerInfoRegistryStorage(caffeineCacheConfig: ToolsCaffeineCacheConfig) = InMemoryMcpServerInfoRegistryStorage(caffeineCacheConfig)
}

@ConfigurationProperties("open-responses.tool.store.caffeine")
data class ToolsCaffeineCacheConfig(
    val maxSize: Long = 500,
    val ttlMinutes: Long = 10,
)
