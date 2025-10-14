package ai.masaic.platform.api.service

import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.tools.CreateMockMcpServerRequest
import ai.masaic.platform.api.tools.FunctionBody
import ai.masaic.platform.api.tools.MockFunctionDefinition
import ai.masaic.platform.api.tools.PlatformMcpService
import ai.masaic.platform.usecases.api.tools.AtomMcpClient
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class AtomMcpServerBootstrapService(
    private val mockFunctionRepository: MockFunctionRepository,
    private val platformMcpService: PlatformMcpService,
) {
    private val log = KotlinLogging.logger { }

    companion object {
        const val ATOM_UPDATE_TOOL_ID = "${AtomMcpClient.ATOM_CALL_LOG_TOOL_NAME}_tool"
        const val ATOM_GET_TOOL_ID = "${AtomMcpClient.ATOM_PAST_CALLS_LOGS_TOOL_NAME}_tool"
        const val ATOM_MCP_SERVER_ID = "atom-agent-server"
        const val ATOM_MCP_SERVER_URL = "https://$ATOM_MCP_SERVER_ID/masaic.ai/api/mcp"
    }

    @EventListener(ApplicationReadyEvent::class)
    suspend fun loadAtomMCPServer() {
        val atomUpdateTool = AtomMcpClient.callLogUpdateToolDef
        var updateToolDef = MockFunctionDefinition(id = ATOM_UPDATE_TOOL_ID, functionBody = FunctionBody(name = atomUpdateTool.name, description = atomUpdateTool.description, parameters = atomUpdateTool.parameters), outputSchem = "")
        updateToolDef = mockFunctionRepository.upsert(updateToolDef)

        val atomGetTool = AtomMcpClient.callLogUpdateToolDef
        var getToolDef = MockFunctionDefinition(id = ATOM_GET_TOOL_ID, functionBody = FunctionBody(name = atomGetTool.name, description = atomGetTool.description, parameters = atomGetTool.parameters), outputSchem = "")
        getToolDef = mockFunctionRepository.upsert(getToolDef)

        platformMcpService.createMockServer(CreateMockMcpServerRequest(id = ATOM_MCP_SERVER_ID, url = ATOM_MCP_SERVER_URL, serverLabel = ATOM_MCP_SERVER_ID, toolIds = listOf(updateToolDef.id, getToolDef.id)))
        log.info { "loaded atom agent mcp server." }
    }
}
