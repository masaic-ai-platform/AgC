package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.PlugableToolAdapter
import ai.masaic.openresponses.tool.PlugableToolDefinition
import ai.masaic.openresponses.tool.PluggedToolRequest
import org.springframework.http.codec.ServerSentEvent

abstract class MultiPlugAdapter(
    private val adapters: List<PlugableToolAdapter>,
) : PlugableToolAdapter

class SimpleMultiPlugAdapter(
    private val adapters: List<PlugableToolAdapter>,
) : PlugableToolAdapter {
    init {
        PlugableToolAdapter.enableAdapter(true)
    }

    override suspend fun plugIn(name: String): PlugableToolDefinition? = findApplicableAdapter(name)?.plugIn(name)

    override suspend fun callTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String? {
        val adapter = findApplicableAdapter(pluggedToolRequest.name) ?: throw MultiPlugAdapterException("Unable to find adapter to call tool ${pluggedToolRequest.name}")
        return adapter.callTool(pluggedToolRequest, eventEmitter)
    }

    private suspend fun findApplicableAdapter(name: String) = adapters.firstOrNull { it.plugIn(name) != null }
}

class MultiPlugAdapterException(
    message: String,
) : RuntimeException(message)
