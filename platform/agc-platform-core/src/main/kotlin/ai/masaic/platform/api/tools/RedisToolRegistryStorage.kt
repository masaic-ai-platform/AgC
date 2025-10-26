package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import ai.masaic.platform.api.config.PlatformInfo
import ai.masaic.platform.api.config.ToolsRedisCacheConfig
import ai.masaic.platform.api.user.UserInfoProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging
import org.redisson.api.RedissonReactiveClient
import java.time.Duration

/**
 * Distributed tool registry storage implementation using Redis.
 *
 * This implementation provides shared storage across multiple instances in a clustered environment.
 * Features:
 * - Distributed caching with Redis via Redisson
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
class RedisToolRegistryStorage(
    private val redissonClient: RedissonReactiveClient,
    private val platformInfo: PlatformInfo,
    private val cacheConfig: ToolsRedisCacheConfig,
) : ToolRegistryStorage {
    private val log = KotlinLogging.logger { }
    private val objectMapper = jacksonObjectMapper()

    override suspend fun <T : ToolDefinition> add(
        toolDefinition: T,
        type: Class<T>,
    ) {
        val key = buildKey(toolDefinition.name, type)
        val json = objectMapper.writeValueAsString(toolDefinition)
        val bucket = redissonClient.getBucket<String>(key)
        bucket
            .set(json, Duration.ofMinutes(cacheConfig.ttlMinutes))
            .awaitFirstOrNull()
        log.debug("Added tool '${toolDefinition.name}' to Redis with key '$key'")
    }

    override suspend fun <T : ToolDefinition> get(
        name: String,
        type: Class<T>,
    ): T? {
        val key = buildKey(name, type)

        // Direct GET operation - no scanning needed
        val bucket = redissonClient.getBucket<String>(key)
        val json = bucket.get().awaitFirstOrNull()

        if (json == null) {
            log.debug("Tool '$name' not found in Redis with key '$key'")
            return null
        }

        // Reset TTL for sliding expiration
        bucket
            .expire(Duration.ofMinutes(cacheConfig.ttlMinutes))
            .awaitFirstOrNull()
        log.debug("Reset TTL for tool '$name' with key '$key'")

        val tool = objectMapper.readValue(json, type)
        val result = if (tool != null && type.isInstance(tool)) type.cast(tool) else null
        log.debug("Retrieved tool '$name' (type: ${type.canonicalName}) from Redis with key '$key': ${if (result != null) "found" else "deserialization failed"}")
        return result
    }

    override suspend fun <T : ToolDefinition> remove(
        name: String,
        type: Class<T>,
    ) {
        val key = buildKey(name, type)
        val bucket = redissonClient.getBucket<String>(key)
        bucket
            .delete()
            .awaitFirstOrNull()
        log.debug("Removed tool '$name' from Redis with key '$key'")
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
            "${platformInfo.env}:${platformInfo.appName}:$userId:tool:${type.canonicalName}:$name"
        } else {
            "${platformInfo.env}:${platformInfo.appName}:tool:${type.canonicalName}:$name"
        }
    }
}
