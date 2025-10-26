package ai.masaic.openresponses.tool.mcp

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration

/**
 * Interface for storing and retrieving MCP clients.
 */
interface McpClientStore {
    /**
     * Adds an MCP client to the store.
     *
     * @param serverName The server name/identifier
     * @param mcpClient The MCP client instance
     */
    suspend fun add(
        serverName: String,
        mcpClient: McpClient,
    )

    /**
     * Retrieves an MCP client if present in the store.
     *
     * @param serverName The server name/identifier
     * @return The MCP client if present, null otherwise
     */
    suspend fun getIfPresent(serverName: String): McpClient?

    /**
     * Gets all clients as a map.
     *
     * @return Map of server names to MCP clients
     */
    suspend fun asMap(): Map<String, McpClient>
}

/**
 * Caffeine-based implementation of McpClientStore.
 *
 * This implementation uses a Caffeine cache with:
 * - Maximum size: 500 entries
 * - TTL: 1 hour
 */
open class CaffeineMcpClientStore : McpClientStore {
    private val cache: Cache<String, McpClient> =
        Caffeine
            .newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build()

    override suspend fun add(
        serverName: String,
        mcpClient: McpClient,
    ) {
        cache.put(buildKey(serverName), mcpClient)
    }

    override suspend fun getIfPresent(serverName: String): McpClient? = cache.getIfPresent(buildKey(serverName))

    override suspend fun asMap(): Map<String, McpClient> = cache.asMap()

    open suspend fun buildKey(serverName: String) = serverName
}
