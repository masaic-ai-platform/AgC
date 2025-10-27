package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.api.config.ToolsCaffeineCacheConfig
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

/**
 * In-memory MCP server info registry storage implementation using Caffeine cache.
 *
 * This implementation provides fast in-memory access with optional TTL.
 * Suitable for single-instance deployments or development environments.
 */
open class InMemoryMcpServerInfoRegistryStorage(
    cacheConfig: ToolsCaffeineCacheConfig,
) : McpServerInfoRegistryStorage {
    private val log = KotlinLogging.logger { }

    private val cache: Cache<String, MCPServerInfo> =
        Caffeine
            .newBuilder()
            .maximumSize(cacheConfig.maxSize)
            .expireAfterWrite(if (cacheConfig.ttlMinutes > 1) cacheConfig.ttlMinutes - 1 else cacheConfig.ttlMinutes, TimeUnit.MINUTES)
            .build()

    override suspend fun add(mcpServerInfo: MCPServerInfo) {
        val key = buildKey(mcpServerInfo.serverIdentifier())
        cache.put(key, mcpServerInfo)
        log.debug("Added MCP server '${mcpServerInfo.id}' to cache with key '$key'")
    }

    override suspend fun get(mcpServerId: String): MCPServerInfo? {
        val serverInfo = cache.getIfPresent(buildKey(mcpServerId))
        log.debug("Retrieved MCP server '$mcpServerId' from cache: ${if (serverInfo != null) "found" else "not found"}")
        return serverInfo
    }

    override suspend fun remove(mcpServerId: String) {
        cache.invalidate(buildKey(mcpServerId))
        log.debug("Removed MCP server '$mcpServerId' from cache")
    }

    open suspend fun buildKey(mcpServerId: String) = mcpServerId
}
