package ai.masaic.platform.api.tools.oauth

import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.platform.api.config.PlatformInfo
import ai.masaic.platform.api.user.UserInfoProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging
import org.redisson.api.RedissonReactiveClient
import java.time.Duration

/**
 * Distributed MCP auth token repository implementation using Redis.
 *
 * This implementation provides shared storage across multiple instances in a clustered environment.
 * Features:
 * - Distributed caching with Redis via Redisson
 * - Fixed 10-hour TTL for OAuth tokens
 * - Multi-tenant support with automatic user context resolution
 * - Environment and app-aware key naming
 *
 * Key format: <env>:<appName>:<userId>:mcp-auth-token:<mcpServerId>
 * Where:
 * - env: Deployment environment (from SPRING_PROFILES_ACTIVE or 'default')
 * - appName: Application name for multi-app isolation
 * - userId: Current user ID from UserInfoProvider (omitted if null)
 * - mcpServerId: MCP server identifier (serverIdentifier() from MCPServerInfo)
 */
class RedisMcpAuthTokenRepository(
    private val redissonClient: RedissonReactiveClient,
    private val platformInfo: PlatformInfo,
) : McpAuthTokenRepository {
    private val log = KotlinLogging.logger { }

    // Configure ObjectMapper with all required modules including JSR310 for Java 8 date/time types
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    override suspend fun put(
        mcpServerInfo: MCPServerInfo,
        tokens: TokenSet,
    ) {
        val key = buildKey(mcpServerInfo.serverIdentifier())
        val json = objectMapper.writeValueAsString(tokens)
        val bucket = redissonClient.getBucket<String>(key)
        bucket
            .set(json, Duration.ofMinutes(10 * 60))
            .awaitFirstOrNull()
        log.debug("Added MCP auth tokens for server '${mcpServerInfo.id}' to Redis with key '$key'")
    }

    override suspend fun get(mcpServerInfo: MCPServerInfo): TokenSet? {
        val key = buildKey(mcpServerInfo.serverIdentifier())

        // Direct GET operation
        val bucket = redissonClient.getBucket<String>(key)
        val json = bucket.get().awaitFirstOrNull()

        if (json == null) {
            log.debug("MCP auth tokens for server '${mcpServerInfo.id}' not found in Redis with key '$key'")
            return null
        }

        val tokenSet = objectMapper.readValue(json, TokenSet::class.java)
        log.debug("Retrieved MCP auth tokens for server '${mcpServerInfo.id}' from Redis with key '$key'")
        return tokenSet
    }

    /**
     * Builds a Redis key for MCP auth tokens.
     * Format: <env>:<appName>:<userId>:mcp-auth-token:<mcpServerId>
     */
    private suspend fun buildKey(mcpServerId: String): String {
        val userId = UserInfoProvider.userId()

        return if (userId != null) {
            "${platformInfo.env}:${platformInfo.appName}:$userId:mcp-auth-token:$mcpServerId"
        } else {
            "${platformInfo.env}:${platformInfo.appName}:mcp-auth-token:$mcpServerId"
        }
    }
}

/**
 * Distributed MCP auth flow meta info repository implementation using Redis.
 *
 * This implementation provides shared storage across multiple instances in a clustered environment.
 * Features:
 * - Distributed caching with Redis via Redisson
 * - Short-lived storage for OAuth flow state (5 minutes fixed TTL)
 * - Atomic consume operation (get + delete)
 * - Multi-tenant support with automatic user context resolution
 * - Environment and app-aware key naming
 *
 * Key format: <env>:<appName>:<userId>:mcp-auth-flow:<state>
 * Where:
 * - env: Deployment environment (from SPRING_PROFILES_ACTIVE or 'default')
 * - appName: Application name for multi-app isolation
 * - userId: Current user ID from UserInfoProvider (omitted if null)
 * - state: OAuth state parameter
 */
class RedisMcpAuthFlowMetaInfoRepository(
    private val redissonClient: RedissonReactiveClient,
    private val platformInfo: PlatformInfo,
) : McpAuthFlowMetaInfoRepository {
    private val log = KotlinLogging.logger { }

    private val objectMapper = jacksonObjectMapper()

    override suspend fun save(
        state: String,
        rec: AuthFlowMetaInfo,
    ) {
        val key = buildKey(state)
        val json = objectMapper.writeValueAsString(rec)
        val bucket = redissonClient.getBucket<String>(key)

        // Use shorter TTL for auth flow (5 minutes for security)
        bucket
            .set(json, Duration.ofMinutes(5))
            .awaitFirstOrNull()
        log.debug("Saved MCP auth flow meta info for state '$state' to Redis with key '$key'")
    }

    override suspend fun consume(state: String): AuthFlowMetaInfo? {
        val key = buildKey(state)

        // Get and delete atomically
        val bucket = redissonClient.getBucket<String>(key)
        val json = bucket.getAndDelete().awaitFirstOrNull()

        if (json == null) {
            log.debug("MCP auth flow meta info for state '$state' not found in Redis with key '$key'")
            return null
        }

        val metaInfo = objectMapper.readValue(json, AuthFlowMetaInfo::class.java)
        log.debug("Consumed MCP auth flow meta info for state '$state' from Redis with key '$key'")
        return metaInfo
    }

    /**
     * Builds a Redis key for MCP auth flow meta info.
     * Format: <env>:<appName>:<userId>:mcp-auth-flow:<state>
     */
    private suspend fun buildKey(state: String) = "${platformInfo.env}:${platformInfo.appName}:mcp-auth-flow:$state"
}
