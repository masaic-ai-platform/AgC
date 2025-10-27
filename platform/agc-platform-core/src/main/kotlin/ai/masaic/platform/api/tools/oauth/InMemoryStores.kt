package ai.masaic.platform.api.tools.oauth

import ai.masaic.openresponses.api.config.ToolsCaffeineCacheConfig
import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.platform.api.user.UserInfoProvider
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

/**
 * In-memory MCP auth token repository implementation using Caffeine cache.
 *
 * This implementation provides fast in-memory access with TTL-based expiration.
 * Suitable for single-instance deployments or development environments.
 */
open class InMemoryMcpAuthTokenRepository(
    cacheConfig: ToolsCaffeineCacheConfig,
) : McpAuthTokenRepository {
    private val log = KotlinLogging.logger { }

    private val cache: Cache<String, TokenSet> =
        Caffeine
            .newBuilder()
            .maximumSize(cacheConfig.maxSize)
            .expireAfterAccess(10 * 60, TimeUnit.MINUTES)
            .build()

    override suspend fun put(
        mcpServerInfo: MCPServerInfo,
        tokens: TokenSet,
    ) {
        val key = buildKey(mcpServerInfo.serverIdentifier())
        cache.put(key, tokens)
        log.debug("Added MCP auth tokens for server '${mcpServerInfo.id}' to cache with key '$key'")
    }

    override suspend fun get(mcpServerInfo: MCPServerInfo): TokenSet? {
        val key = buildKey(mcpServerInfo.serverIdentifier())
        val tokens = cache.getIfPresent(key)
        log.debug("Retrieved MCP auth tokens for server '${mcpServerInfo.id}' from cache: ${if (tokens != null) "found" else "not found"}")
        return tokens
    }

    private suspend fun buildKey(mcpServerId: String): String {
        val userId = UserInfoProvider.userId()
        return if (userId != null) {
            "$userId:mcp-auth-token:$mcpServerId"
        } else {
            "mcp-auth-token:$mcpServerId"
        }
    }
}

/**
 * In-memory MCP auth flow meta info repository implementation using Caffeine cache.
 *
 * This implementation provides fast in-memory access with short-lived TTL for OAuth flow state.
 * Suitable for single-instance deployments or development environments.
 */
open class InMemoryMcpAuthFlowMetaInfoRepository(
    cacheConfig: ToolsCaffeineCacheConfig,
) : McpAuthFlowMetaInfoRepository {
    private val log = KotlinLogging.logger { }

    private val cache: Cache<String, AuthFlowMetaInfo> =
        Caffeine
            .newBuilder()
            .maximumSize(cacheConfig.maxSize)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build()

    override suspend fun save(
        state: String,
        rec: AuthFlowMetaInfo,
    ) {
        cache.put(buildKey(state), rec)
        log.debug("Saved MCP auth flow meta info for state '$state' to cache")
    }

    override suspend fun consume(state: String): AuthFlowMetaInfo? {
        val rec = cache.getIfPresent(buildKey(state))
        if (rec != null) {
            cache.invalidate(state)
            log.debug("Consumed MCP auth flow meta info for state '$state' from cache")
        } else {
            log.debug("MCP auth flow meta info for state '$state' not found in cache")
        }
        return rec
    }

    open suspend fun buildKey(state: String) = state
}
