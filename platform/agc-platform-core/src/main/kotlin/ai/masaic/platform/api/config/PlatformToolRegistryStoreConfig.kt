package ai.masaic.platform.api.config

import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import ai.masaic.platform.api.tools.RedisToolRegistryStorage
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory

@Configuration
class PlatformToolRegistryStoreConfig {
    @Bean
    @ConditionalOnProperty(name = ["open-responses.tool.registry.store.type"], havingValue = "redis")
    @ConditionalOnMissingBean(ToolRegistryStorage::class)
    fun redisToolRegistryStorage(
        connectionFactory: ReactiveRedisConnectionFactory,
        platformInfo: PlatformInfo,
        cacheConfig: ToolsRedisCacheConfig,
    ) = RedisToolRegistryStorage(connectionFactory, platformInfo, cacheConfig)
}

@ConfigurationProperties("open-responses.tool.registry.store.redis")
data class ToolsRedisCacheConfig(
    val ttlMinutes: Long = 10,
)
