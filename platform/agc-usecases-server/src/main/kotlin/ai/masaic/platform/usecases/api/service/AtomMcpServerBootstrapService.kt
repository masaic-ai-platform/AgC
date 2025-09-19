package ai.masaic.platform.usecases.api.service

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
import java.util.*

@Service
class AtomMcpServerBootstrapService(
    private val mockFunctionRepository: MockFunctionRepository,
    private val platformMcpService: PlatformMcpService,
) {
    private val log = KotlinLogging.logger { }

    companion object {
        const val ATOM_TOOL_ID = "${AtomMcpClient.ATOM_TOOL_NAME}_tool"
        const val ATOM_MCP_SERVER_ID = "atom-agent-server"
        const val ATOM_MCP_SERVER_URL = "https://$ATOM_MCP_SERVER_ID/masaic.ai/api/mcp"
    }

    @EventListener(ApplicationReadyEvent::class)
    suspend fun loadAtomMCPServer() {
        val atomTool = AtomMcpClient.atomToolDef
        var mockFunctionDefinition = MockFunctionDefinition(id = ATOM_TOOL_ID, functionBody = FunctionBody(name = atomTool.name, description = atomTool.description, parameters = atomTool.parameters), outputSchem = "")
        mockFunctionDefinition = mockFunctionRepository.upsert(mockFunctionDefinition)
        platformMcpService.createMockServer(CreateMockMcpServerRequest(id = ATOM_MCP_SERVER_ID, url = ATOM_MCP_SERVER_URL, serverLabel = ATOM_MCP_SERVER_ID, toolIds = listOf(mockFunctionDefinition.id)))
        log.info { "loaded atom agent mcp server." }
    }
}
