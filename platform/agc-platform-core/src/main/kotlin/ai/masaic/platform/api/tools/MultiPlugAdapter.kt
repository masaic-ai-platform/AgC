package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.PlugableToolAdapter
import ai.masaic.openresponses.tool.PlugableToolDefinition
import ai.masaic.openresponses.tool.PluggedToolRequest
import org.springframework.http.codec.ServerSentEvent

abstract class MultiPlugAdapter(
    private val adapters: List<PlugableToolAdapter>,
) : PlugableToolAdapter

class NoOpMultiPlugAdapter(
    private val adapters: List<PlugableToolAdapter>? = null,
) : PlugableToolAdapter {
    init {
        PlugableToolAdapter.enableAdapter(false)
    }

    override suspend fun callTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String? = null
}

class SimpleMultiPlugAdapter(
    private val adapters: List<PlugableToolAdapter>,
) : PlugableToolAdapter {
    init {
        PlugableToolAdapter.enableAdapter(true)
    }

    override fun plugIn(name: String): PlugableToolDefinition? = findPluggedTool(name)

    override suspend fun callTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String =
        findPluggedTool(pluggedToolRequest.name)?.let {
            callTool(pluggedToolRequest, eventEmitter)
        } ?: throw MultiPlugAdapterException("No tool ${pluggedToolRequest.name} plugged into Multi plug adapter")

    private fun findPluggedTool(name: String) = adapters.firstOrNull { it.plugIn(name) != null }?.plugIn(name)
}

class MultiPlugAdapterException(
    message: String,
) : RuntimeException(message)
