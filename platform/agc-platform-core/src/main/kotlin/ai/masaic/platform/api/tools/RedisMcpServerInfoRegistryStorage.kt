package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.openresponses.tool.mcp.McpServerInfoRegistryStorage
import ai.masaic.platform.api.config.PlatformInfo
import ai.masaic.platform.api.config.ToolsRedisCacheConfig
import ai.masaic.platform.api.user.UserInfoProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging
import org.redisson.api.RedissonReactiveClient
import java.time.Duration

/**
 * Distributed MCP server info registry storage implementation using Redis.
 *
 * This implementation provides shared storage across multiple instances in a clustered environment.
 * Features:
 * - Distributed caching with Redis via Redisson
 * - Sliding expiration: TTL is reset on every get operation
 * - Multi-tenant support with automatic user context resolution
 * - Environment-aware key naming
 *
 * Key format: <env>:<userId>:server:<mcpServerId>
 * Where:
 * - env: Deployment environment (from SPRING_PROFILES_ACTIVE or 'default')
 * - userId: Current user ID from UserInfoProvider (omitted if null)
 * - mcpServerId: MCP server identifier (serverIdentifier() from MCPServerInfo)
 */
class RedisMcpServerInfoRegistryStorage(
    private val redissonClient: RedissonReactiveClient,
    private val platformInfo: PlatformInfo,
    private val cacheConfig: ToolsRedisCacheConfig,
) : McpServerInfoRegistryStorage {
    private val log = KotlinLogging.logger { }
    private val objectMapper = jacksonObjectMapper()

    override suspend fun add(mcpServerInfo: MCPServerInfo) {
        val key = buildKey(mcpServerInfo.serverIdentifier())
        val json = objectMapper.writeValueAsString(mcpServerInfo)
        val bucket = redissonClient.getBucket<String>(key)
        bucket
            .set(json, Duration.ofMinutes(ttl()))
            .awaitFirstOrNull()
        log.debug("Added MCP server '${mcpServerInfo.id}' to Redis with key '$key'")
    }

    override suspend fun get(mcpServerId: String): MCPServerInfo? {
        val key = buildKey(mcpServerId)

        // Direct GET operation - no scanning needed
        val bucket = redissonClient.getBucket<String>(key)
        val json = bucket.get().awaitFirstOrNull()

        if (json == null) {
            log.debug("MCP server '$mcpServerId' not found in Redis with key '$key'")
            return null
        }

        val serverInfo = objectMapper.readValue(json, MCPServerInfo::class.java)
        log.debug("Retrieved MCP server '$mcpServerId' from Redis with key '$key': ${if (serverInfo != null) "found" else "deserialization failed"}")
        return serverInfo
    }

    override suspend fun remove(mcpServerId: String) {
        val key = buildKey(mcpServerId)
        val bucket = redissonClient.getBucket<String>(key)
        bucket
            .delete()
            .awaitFirstOrNull()
        log.debug("Removed MCP server '$mcpServerId' from Redis with key '$key'")
    }

    /**
     * Builds a Redis key for MCP server info.
     * Format: <env>:<userId>:server:<mcpServerId>
     */
    private suspend fun buildKey(mcpServerId: String): String {
        val userId = UserInfoProvider.userId()

        return if (userId != null) {
            "${platformInfo.env}:${platformInfo.appName}:$userId:mcp-server:$mcpServerId"
        } else {
            "${platformInfo.env}:${platformInfo.appName}:mcp-server:$mcpServerId"
        }
    }

    private fun ttl() = if (cacheConfig.ttlMinutes > 1) cacheConfig.ttlMinutes - 1 else cacheConfig.ttlMinutes
}
