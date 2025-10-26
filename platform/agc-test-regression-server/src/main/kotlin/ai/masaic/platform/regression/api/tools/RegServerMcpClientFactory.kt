package ai.masaic.platform.regression.api.tools

import ai.masaic.openresponses.api.model.ModelSettings
import ai.masaic.openresponses.tool.mcp.McpClient
import ai.masaic.platform.api.repository.McpMockServerRepository
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.tools.PlatformMcpClientFactory
import ai.masaic.platform.regression.api.service.RegSuiteResponseStoreFacade
import java.net.URI

class RegServerMcpClientFactory(
    mockServerRepository: McpMockServerRepository,
    mockFunRepository: MockFunctionRepository,
    mocksRepository: MocksRepository,
    modelSettings: ModelSettings,
    modelService: ModelService,
    private val responseStoreFacade: RegSuiteResponseStoreFacade,
) : PlatformMcpClientFactory(mockServerRepository, mockFunRepository, mocksRepository, modelSettings, modelService) {
    override suspend fun init(
        serverName: String,
        url: String,
        headers: Map<String, String>,
    ): McpClient {
        val uri = URI(url)
        if (uri.host.contains(RegSuiteMcpClient.REGRESS_SERVER_HOST)) {
            return RegSuiteMcpClient(responseStoreFacade)
        }

        return super.init(serverName, url, headers)
    }
}
