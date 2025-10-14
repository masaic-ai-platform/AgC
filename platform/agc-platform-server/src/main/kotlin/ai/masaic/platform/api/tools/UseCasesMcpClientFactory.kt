package ai.masaic.platform.usecases.api.tools

import ai.masaic.openresponses.api.model.ModelSettings
import ai.masaic.openresponses.tool.mcp.McpClient
import ai.masaic.openresponses.tool.mcp.oauth.MCPOAuthService
import ai.masaic.platform.api.repository.McpMockServerRepository
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.AtomMcpServerBootstrapService
import ai.masaic.platform.api.service.AtomTemporalWorkflowService
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.tools.PlatformMcpClientFactory
import org.springframework.stereotype.Component

@Component
class UseCasesMcpClientFactory(
    mockServerRepository: McpMockServerRepository,
    mockFunRepository: MockFunctionRepository,
    mocksRepository: MocksRepository,
    modelSettings: ModelSettings,
    modelService: ModelService,
    mcpoAuthService: MCPOAuthService,
    private val temporalService: AtomTemporalWorkflowService,
) : PlatformMcpClientFactory(mockServerRepository, mockFunRepository, mocksRepository, modelSettings, modelService, mcpoAuthService) {
    override suspend fun init(
        serverName: String,
        url: String,
        headers: Map<String, String>,
    ): McpClient {
        if (url == AtomMcpServerBootstrapService.ATOM_MCP_SERVER_URL) {
            return AtomMcpClient(temporalService)
        }

        return super.init(serverName, url, headers)
    }
}
