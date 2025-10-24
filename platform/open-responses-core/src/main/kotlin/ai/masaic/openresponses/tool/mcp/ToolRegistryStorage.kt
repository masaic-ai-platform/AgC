package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.tool.ToolDefinition

/**
 * Generic storage interface for tool definitions.
 * Supports multi-tenant storage with automatic user context resolution.
 * 
 * All lookups require a tool type for O(1) performance in distributed storage.
 */
interface ToolRegistryStorage {
    /**
     * Adds a tool definition to the storage.
     *
     * @param toolDefinition The tool to store
     */
    suspend fun <T : ToolDefinition> add(
        toolDefinition: T,
        type: Class<T>,
    )

    /**
     * Retrieves a tool definition with known type for optimized lookup.
     * For Redis storage, this enables direct key lookup (O(1)) without scanning.
     *
     * @param name Tool name to retrieve
     * @param type The specific ToolDefinition class to retrieve
     * @return ToolDefinition if found, null otherwise
     */
    suspend fun <T : ToolDefinition> get(
        name: String,
        type: Class<T>,
    ): T?

    /**
     * Removes a tool definition.
     *
     * @param name Tool name to remove
     */
    suspend fun <T : ToolDefinition> remove(
        name: String,
        type: Class<T>,
    )
}

/**
 * Reified convenience extension for type-safe retrieval.
 * Usage: storage.get<McpToolDefinition>("weather_tool")
 */
suspend inline fun <reified T : ToolDefinition> ToolRegistryStorage.get(name: String): T? = get(name, T::class.java) as? T

/**
 * Reified convenience extension for type-safe removal.
 * Usage: storage.remove<McpToolDefinition>("weather_tool")
 */
suspend inline fun <reified T : ToolDefinition> ToolRegistryStorage.remove(name: String): T? = remove(name, T::class.java) as? T

/**
 * Reified convenience extension for type-safe addition.
 * Usage: storage.add<McpToolDefinition>("weather_tool")
 */
suspend inline fun <reified T : ToolDefinition> ToolRegistryStorage.add(toolDefinition: T): T? = add(toolDefinition, T::class.java) as? T
