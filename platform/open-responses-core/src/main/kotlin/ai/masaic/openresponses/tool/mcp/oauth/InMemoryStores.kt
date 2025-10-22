package ai.masaic.openresponses.tool.mcp.oauth

import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class InMemoryMcpAuthTokenRepository(
    expireAfterAccess: Duration = Duration.ofHours(24),
) : McpAuthTokenRepository {
    private val cache = Caffeine.newBuilder().expireAfterAccess(expireAfterAccess).build<String, TokenSet>()

    override fun put(
        mcpServerInfo: MCPServerInfo,
        tokens: TokenSet,
    ) {
        cache.put(mcpServerInfo.serverIdentifier(), tokens)
    }

    override fun get(mcpServerInfo: MCPServerInfo): TokenSet? = cache.getIfPresent(mcpServerInfo.serverIdentifier())
}

@Component
class InMemoryMcpAuthFlowMetaInfoRepository(
    expireAfterWrite: Duration = Duration.ofMinutes(10),
) : McpAuthFlowMetaInfoRepository {
    private val cache = Caffeine.newBuilder().expireAfterWrite(expireAfterWrite).build<String, AuthFlowMetaInfo>()

    override fun save(
        state: String,
        rec: AuthFlowMetaInfo,
    ) {
        cache.put(state, rec)
    }

    override fun consume(state: String): AuthFlowMetaInfo? {
        val rec = cache.getIfPresent(state)
        if (rec != null) cache.invalidate(state)
        return rec
    }
}
