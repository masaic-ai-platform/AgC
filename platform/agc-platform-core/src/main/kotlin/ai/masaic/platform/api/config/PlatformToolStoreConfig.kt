package ai.masaic.platform.api.config

import ai.masaic.openresponses.api.config.ToolsCaffeineCacheConfig
import ai.masaic.openresponses.tool.mcp.McpServerInfoRegistryStorage
import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import ai.masaic.platform.api.tools.PlatformInMemoryMcpServerInfoRegistryStorage
import ai.masaic.platform.api.tools.PlatformInMemoryToolRegistryStorage
import ai.masaic.platform.api.tools.RedisMcpServerInfoRegistryStorage
import ai.masaic.platform.api.tools.RedisToolRegistryStorage
import ai.masaic.platform.api.tools.oauth.*
import org.redisson.api.RedissonReactiveClient
import org.redisson.spring.starter.RedissonAutoConfigurationV2
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ConditionalOnProperty(name = ["open-responses.tool.store.type"], havingValue = "redis")
@EnableConfigurationProperties(ToolsRedisCacheConfig::class)
@Import(RedissonAutoConfigurationV2::class)
class RedisToolRegistryStoreConfig {
    @Bean
    @ConditionalOnMissingBean(ToolRegistryStorage::class)
    fun redisToolRegistryStorage(
        redissonClient: RedissonReactiveClient,
        platformInfo: PlatformInfo,
        cacheConfig: ToolsRedisCacheConfig,
    ) = RedisToolRegistryStorage(redissonClient, platformInfo, cacheConfig)

    @Bean
    @ConditionalOnMissingBean(McpServerInfoRegistryStorage::class)
    fun redisMcpServerInfoRegistryStorage(
        redissonClient: RedissonReactiveClient,
        platformInfo: PlatformInfo,
        cacheConfig: ToolsRedisCacheConfig,
    ) = RedisMcpServerInfoRegistryStorage(redissonClient, platformInfo, cacheConfig)

    @Bean
    @ConditionalOnMissingBean(McpAuthTokenRepository::class)
    fun redisMcpAuthTokenRepository(
        redissonClient: RedissonReactiveClient,
        platformInfo: PlatformInfo,
    ) = RedisMcpAuthTokenRepository(redissonClient, platformInfo)

    @Bean
    @ConditionalOnMissingBean(McpAuthFlowMetaInfoRepository::class)
    fun redisMcpAuthFlowMetaInfoRepository(
        redissonClient: RedissonReactiveClient,
        platformInfo: PlatformInfo,
    ) = RedisMcpAuthFlowMetaInfoRepository(redissonClient, platformInfo)
}

@Configuration
@ConditionalOnProperty(name = ["open-responses.tool.store.type"], havingValue = "in-memory", matchIfMissing = true)
@EnableConfigurationProperties(ToolsCaffeineCacheConfig::class)
class PlatformInMemoryToolStoreConfig {
    @Bean
    @ConditionalOnMissingBean(ToolRegistryStorage::class)
    fun caffeineToolRegistryStorage(caffeineCacheConfig: ToolsCaffeineCacheConfig) = PlatformInMemoryToolRegistryStorage(caffeineCacheConfig)

    @Bean
    @ConditionalOnMissingBean(McpServerInfoRegistryStorage::class)
    fun caffeineMcpServerInfoRegistryStorage(caffeineCacheConfig: ToolsCaffeineCacheConfig) = PlatformInMemoryMcpServerInfoRegistryStorage(caffeineCacheConfig)

    @Bean
    @ConditionalOnMissingBean(McpAuthTokenRepository::class)
    fun caffeineMcpAuthTokenRepository(caffeineCacheConfig: ToolsCaffeineCacheConfig) = InMemoryMcpAuthTokenRepository(caffeineCacheConfig)

    @Bean
    @ConditionalOnMissingBean(McpAuthFlowMetaInfoRepository::class)
    fun caffeineMcpAuthFlowMetaInfoRepository(caffeineCacheConfig: ToolsCaffeineCacheConfig) = InMemoryMcpAuthFlowMetaInfoRepository(caffeineCacheConfig)
}

@ConfigurationProperties("open-responses.tool.store.redis")
data class ToolsRedisCacheConfig(
    val ttlMinutes: Long = 10,
)
