package ai.masaic.platform.regression.api.tools

import ai.masaic.openresponses.api.service.ResponseStoreService
import ai.masaic.openresponses.tool.mcp.McpClient
import ai.masaic.openresponses.tool.mcp.oauth.MCPOAuthService
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.repository.McpMockServerRepository
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.tools.PlatformMcpClientFactory
import java.net.URI

class RegServerMcpClientFactory(
    mockServerRepository: McpMockServerRepository,
    mockFunRepository: MockFunctionRepository,
    mocksRepository: MocksRepository,
    modelSettings: ModelSettings,
    modelService: ModelService,
    mcpoAuthService: MCPOAuthService,
    private val responseStoreService: ResponseStoreService,
) : PlatformMcpClientFactory(mockServerRepository, mockFunRepository, mocksRepository, modelSettings, modelService, mcpoAuthService) {
    override suspend fun init(
        serverName: String,
        url: String,
        headers: Map<String, String>,
    ): McpClient {
        val uri = URI(url)
        if (uri.host.contains(RegSuiteMcpClient.REGRESS_SERVER_HOST)) {
            return RegSuiteMcpClient(responseStoreService)
        }

        return super.init(serverName, url, headers)
    }
}
