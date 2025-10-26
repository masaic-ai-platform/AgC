package ai.masaic.openresponses.tool.mcp

/**
 * Generic storage interface for MCP server information.
 * Supports multi-tenant storage with automatic user context resolution.
 */
interface McpServerInfoRegistryStorage {
    /**
     * Adds an MCP server info to the storage.
     *
     * @param mcpServerInfo The server info to store
     */
    suspend fun add(mcpServerInfo: MCPServerInfo)

    /**
     * Retrieves an MCP server info by server identifier.
     *
     * @param mcpServerId Server identifier to retrieve
     * @return MCPServerInfo if found, null otherwise
     */
    suspend fun get(mcpServerId: String): MCPServerInfo?

    /**
     * Removes an MCP server info.
     *
     * @param mcpServerId Server identifier to remove
     */
    suspend fun remove(mcpServerId: String)
}
