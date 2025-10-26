package ai.masaic.platform.api.tools

import ai.masaic.openresponses.api.config.ToolsCaffeineCacheConfig
import ai.masaic.openresponses.tool.mcp.InMemoryMcpServerInfoRegistryStorage
import ai.masaic.openresponses.tool.mcp.InMemoryToolRegistryStorage
import ai.masaic.platform.api.user.UserInfoProvider

class PlatformInMemoryMcpServerInfoRegistryStorage(
    cacheConfig: ToolsCaffeineCacheConfig,
) : InMemoryMcpServerInfoRegistryStorage(cacheConfig) {
    override suspend fun buildKey(mcpServerId: String): String {
        val userId = UserInfoProvider.userId()
        return if (userId != null) {
            "$userId:mcp-server:$mcpServerId"
        } else {
            "mcp-server:$mcpServerId"
        }
    }
}

class PlatformInMemoryToolRegistryStorage(
    caffeineCacheConfig: ToolsCaffeineCacheConfig,
) : InMemoryToolRegistryStorage(caffeineCacheConfig) {
    override suspend fun buildKey(
        name: String,
        type: Class<*>,
    ): String {
        val userId = UserInfoProvider.userId()
        return if (userId != null) {
            "$userId:tool:${type.canonicalName}:$name"
        } else {
            "tool:${type.canonicalName}:$name"
        }
    }
}
