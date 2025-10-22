package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.PlugableToolDefinition
import ai.masaic.platform.api.user.UserInfoProvider
import org.springframework.stereotype.Component

interface PluggedToolsRegistry {
    suspend fun add(plugableToolDefinition: PlugableToolDefinition)

    suspend fun get(name: String): PlugableToolDefinition?

    suspend fun invalidate(name: String)

    suspend fun toolKey(name: String): String
}

@Component
class UserContextAwarePluggedToolsRegistry : PluggedToolsRegistry {
    private val store = mutableMapOf<String, PlugableToolDefinition>()

    override suspend fun add(plugableToolDefinition: PlugableToolDefinition) {
        store[toolKey(plugableToolDefinition.name)] = plugableToolDefinition
    }

    override suspend fun get(name: String): PlugableToolDefinition? = store[toolKey(name)]

    override suspend fun invalidate(name: String) {
        store.remove(toolKey(name))
    }

    override suspend fun toolKey(name: String): String {
        val userId = UserInfoProvider.userId() ?: throw MultiPlugAdapterException("unable to find userId in the request context.")
        return "$userId.$name"
    }
}
