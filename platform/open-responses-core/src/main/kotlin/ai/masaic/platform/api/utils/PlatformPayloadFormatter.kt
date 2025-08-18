package ai.masaic.platform.api.utils

import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.api.model.MasaicManagedTool
import ai.masaic.openresponses.api.model.PyFunTool
import ai.masaic.openresponses.api.model.Tool
import ai.masaic.openresponses.api.utils.PayloadFormatter
import ai.masaic.openresponses.tool.ToolService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class PlatformPayloadFormatter(
    private val toolService: ToolService,
    private val mapper: ObjectMapper,
) : PayloadFormatter(toolService, mapper) {
    override suspend fun updateToolsInRequest(tools: List<Tool>?): MutableList<Tool>? {
        val updatedTools = mutableListOf<Tool>()
        tools?.forEach {
            when (it) {
                is MasaicManagedTool -> {
                    val tool =
                        toolService.getFunctionTool(it.type) ?: throw ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Define tool ${it.type} properly",
                        )
                    updatedTools.add(tool)
                }

                is MCPTool -> {
                    val mcpToolFunctions = toolService.getRemoteMcpTools(it)
                    if (mcpToolFunctions.isEmpty()) throw IllegalStateException("No MCP tools found for ${it.serverLabel}, ${it.serverUrl}")
                    updatedTools.addAll(mcpToolFunctions)
                }

                is PyFunTool -> {
                    updatedTools.add(toolService.getPyFunTool(it))
                }

                else -> {
                    updatedTools.add(it)
                }
            }
        }
        return updatedTools
    }
}
