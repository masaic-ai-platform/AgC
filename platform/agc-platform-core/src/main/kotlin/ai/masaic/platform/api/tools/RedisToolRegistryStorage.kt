package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import ai.masaic.platform.api.config.PlatformInfo
import ai.masaic.platform.api.config.ToolsRedisCacheConfig
import ai.masaic.platform.api.user.UserInfoProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Distributed tool registry storage implementation using Redis.
 *
 * This implementation provides shared storage across multiple instances in a clustered environment.
 * Features:
 * - Distributed caching with Redis
 * - Sliding expiration: TTL is reset on every get operation
 * - Polymorphic tool definition serialization/deserialization
 * - Multi-tenant support with automatic user context resolution
 * - Environment-aware key naming
 *
 * Key format: <env>:<userId>:tool:<tool_type>:<name>
 * Where:
 * - env: Deployment environment (from SPRING_PROFILES_ACTIVE or 'default')
 * - userId: Current user ID from UserInfoProvider (omitted if null)
 * - tool_type: Type of tool (mcp, native, pyfun, plugable, file_search)
 * - name: Tool name
 */
@Component
@ConditionalOnProperty(
    name = ["tool.registry.storage.type"],
    havingValue = "redis",
)
class RedisToolRegistryStorage(
    connectionFactory: ReactiveRedisConnectionFactory,
    private val platformInfo: PlatformInfo,
    private val cacheConfig: ToolsRedisCacheConfig,
) : ToolRegistryStorage {
    private val log = KotlinLogging.logger { }
    private val objectMapper = jacksonObjectMapper()

    private val redisTemplate: ReactiveStringRedisTemplate =
        ReactiveStringRedisTemplate(connectionFactory)

    override suspend fun <T : ToolDefinition> add(
        toolDefinition: T,
        type: Class<T>,
    ) {
        val key = buildKey(toolDefinition.name, type)
        val json = objectMapper.writeValueAsString(toolDefinition)
        redisTemplate
            .opsForValue()
            .set(key, json, Duration.ofMinutes(cacheConfig.ttlMinutes))
            .awaitSingle()
        log.debug("Added tool '${toolDefinition.name}' to Redis with key '$key'")
    }

    override suspend fun <T : ToolDefinition> get(
        name: String,
        type: Class<T>,
    ): T? {
        val key = buildKey(name, type)

        // Direct GET operation - no scanning needed
        val json =
            redisTemplate
                .opsForValue()
                .get(key)
                .awaitSingle()

        redisTemplate
            .expire(key, Duration.ofMinutes(cacheConfig.ttlMinutes))
            .awaitSingle()
        log.debug("Reset TTL for tool '$name' with key '$key'")

        val tool = objectMapper.readValue(json, type::class.java)
        val result = if (tool != null && type.isInstance(tool)) type.cast(tool) else null
        log.debug("Retrieved tool '$name' (type: ${type.canonicalName}) from Redis with key '$key': ${if (result != null) "found" else "deserialization failed"}")
        return result
    }

    override suspend fun <T : ToolDefinition> remove(
        name: String,
        type: Class<T>,
    ) {
        val key = buildKey(name, type)
        redisTemplate
            .delete(key)
            .awaitSingle()
    }

    /**
     * Builds a Redis key with a specific tool type.
     * Format: <env>:<userId>:tool:<tool_type>:<name>
     */
    private suspend fun buildKey(
        name: String,
        type: Class<*>,
    ): String {
        val userId = UserInfoProvider.userId()

        return if (userId != null) {
            "${platformInfo.env}:$userId:tool:${type.canonicalName}:$name"
        } else {
            "${platformInfo.env}:tool:${type.canonicalName}:$name"
        }
    }
}
