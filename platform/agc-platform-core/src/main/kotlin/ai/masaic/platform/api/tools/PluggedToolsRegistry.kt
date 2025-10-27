package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.PlugableToolDefinition
import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import ai.masaic.openresponses.tool.mcp.add
import ai.masaic.openresponses.tool.mcp.get
import ai.masaic.openresponses.tool.mcp.remove
import ai.masaic.platform.api.user.UserInfoProvider
import org.springframework.stereotype.Component

interface PluggedToolsRegistry {
    suspend fun add(plugableToolDefinition: PlugableToolDefinition)

    suspend fun get(name: String): PlugableToolDefinition?

    suspend fun invalidate(name: String)

    suspend fun toolKey(name: String): String
}

@Component
class UserContextAwarePluggedToolsRegistry(
    private val toolStorage: ToolRegistryStorage,
) : PluggedToolsRegistry {
    override suspend fun add(plugableToolDefinition: PlugableToolDefinition) {
        toolStorage.add<PlugableToolDefinition>(plugableToolDefinition)
    }

    override suspend fun get(name: String): PlugableToolDefinition? = toolStorage.get<PlugableToolDefinition>(name)

    override suspend fun invalidate(name: String) {
        toolStorage.remove<PlugableToolDefinition>(name)
    }

    override suspend fun toolKey(name: String): String {
        val userId = UserInfoProvider.userId() ?: throw MultiPlugUntraceableToolException("unable to find userId in the request context.")
        return "$userId.$name"
    }
}
