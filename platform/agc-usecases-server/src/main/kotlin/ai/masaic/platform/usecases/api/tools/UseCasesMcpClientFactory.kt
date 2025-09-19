package ai.masaic.platform.usecases.api.tools

import ai.masaic.openresponses.tool.mcp.McpClient
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.repository.McpMockServerRepository
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.tools.PlatformMcpClientFactory
import ai.masaic.platform.usecases.api.service.AtomMcpServerBootstrapService
import org.springframework.stereotype.Component

@Component
class UseCasesMcpClientFactory(
    mockServerRepository: McpMockServerRepository,
    mockFunRepository: MockFunctionRepository,
    mocksRepository: MocksRepository,
    modelSettings: ModelSettings,
    modelService: ModelService,
) : PlatformMcpClientFactory(mockServerRepository, mockFunRepository, mocksRepository, modelSettings, modelService) {
    override suspend fun init(
        serverName: String,
        url: String,
        headers: Map<String, String>,
    ): McpClient {
        if (url == AtomMcpServerBootstrapService.ATOM_MCP_SERVER_URL) {
            return AtomMcpClient()
        }

        return super.init(serverName, url, headers)
    }
}
