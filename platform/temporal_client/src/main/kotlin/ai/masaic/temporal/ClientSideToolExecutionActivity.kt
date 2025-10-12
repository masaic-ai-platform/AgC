package ai.masaic.temporal

import ai.masaic.openresponses.tool.PluggedToolRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.temporal.activity.ActivityInterface
import org.slf4j.LoggerFactory

@ActivityInterface
interface ClientSideToolExecutionActivity {
    fun executeTool(pluggedToolRequest: PluggedToolRequest): String
}

class ClientSideToolExecutionActivityImpl : ClientSideToolExecutionActivity {
    private val mapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(ClientSideToolExecutionActivityImpl::class.java)

    override fun executeTool(pluggedToolRequest: PluggedToolRequest): String {
        logger.info("###### Place holder for custom implementing at client side ############ ")
        logger.debug("Executing tool with arguments: {}", pluggedToolRequest.arguments)
        val result = "Executed Successfully"
        return result
    }
}
