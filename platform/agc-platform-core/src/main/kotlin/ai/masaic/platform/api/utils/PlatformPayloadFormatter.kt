package ai.masaic.platform.api.utils

import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.model.PyFunTool
import ai.masaic.openresponses.api.utils.PayloadFormatter
import ai.masaic.openresponses.tool.mcp.McpToolNotFoundException
import ai.masaic.platform.api.tools.PlatformToolService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class PlatformPayloadFormatter(
    private val toolService: PlatformToolService,
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
                    if (mcpToolFunctions.isEmpty()) throw McpToolNotFoundException("No MCP tools found for ${it.serverLabel}, ${it.serverUrl}")
                    updatedTools.addAll(mcpToolFunctions)
                }

                is PyFunTool -> {
                    updatedTools.add(toolService.getPyFunTool(it))
                }

                is FunctionTool -> {
                    val derivedTool = toolService.derivePluggableTool(it)
                    derivedTool?.let { updatedTools.add(derivedTool) } ?: updatedTools.add(it)
                }

                else -> {
                    updatedTools.add(it)
                }
            }
        }
        return updatedTools
    }
}
