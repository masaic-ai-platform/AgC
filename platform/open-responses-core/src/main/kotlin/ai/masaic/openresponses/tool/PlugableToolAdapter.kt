package ai.masaic.openresponses.tool

import ai.masaic.openresponses.api.model.FunctionTool
import ai.masaic.openresponses.api.utils.LoopContextInfo
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import java.util.*

interface PlugableToolAdapter {
    companion object {
        @Volatile
        private var isEnabledFlag: Boolean = false

        fun isEnabled() = isEnabledFlag

        fun enableAdapter(flag: Boolean) {
            isEnabledFlag = flag
        }
    }

    suspend fun callTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String?

    fun plugIn(name: String): PlugableToolDefinition? =
        if (isEnabled()) {
            PlugableToolDefinition(name = name, description = "This tool will execute externally but execution is managed by AgC", parameters = mutableMapOf(), eventMeta = ToolProgressEventMeta(infix = "agc"))
        } else {
            null
        }
}

class NoOpPlugableToolAdapter : PlugableToolAdapter {
    private val log = KotlinLogging.logger { }

    override suspend fun callTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String {
        val response = "This is NoOpPlugableToolAdapter, it returns the input as it is. Input received: $pluggedToolRequest"
        log.info { response }
        return response
    }
}

data class PluggedToolRequest(
    val name: String,
    val arguments: String,
    val loopContextInfo: LoopContextInfo,
)

data class PlugableToolDefinition(
    override val id: String = UUID.randomUUID().toString(),
    override val protocol: ToolProtocol = ToolProtocol.PLUGABLE,
    override val hosting: ToolHosting = ToolHosting.MASAIC_PLUG,
    override val name: String,
    override val description: String,
    val parameters: MutableMap<String, Any>,
    override val eventMeta: ToolProgressEventMeta? = null,
) : ToolDefinition(id, protocol, hosting, name, description, eventMeta) {
    fun toFunctionTool(): FunctionTool =
        FunctionTool(
            description = description,
            name = name,
            parameters = parameters,
            strict = true,
        )
}
