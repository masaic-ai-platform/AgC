package ai.masaic.platform.regression.api.config

import ai.masaic.openresponses.api.model.ModelSettings
import ai.masaic.openresponses.api.service.ResponseStoreService
import ai.masaic.openresponses.tool.mcp.oauth.MCPOAuthService
import ai.masaic.platform.api.repository.McpMockServerRepository
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.regression.api.tools.RegServerMcpClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
class RegServerCoreConfig {
    @Bean
    fun mcpClientFactory(
        mcpMockServerRepository: McpMockServerRepository,
        mockFunctionRepository: MockFunctionRepository,
        mocksRepository: MocksRepository,
        modelSettings: ModelSettings,
        @Lazy modelService: ModelService,
        @Lazy responseStoreService: ResponseStoreService,
        mcpoAuthService: MCPOAuthService,
    ) = RegServerMcpClientFactory(mcpMockServerRepository, mockFunctionRepository, mocksRepository, modelSettings, modelService, mcpoAuthService, responseStoreService)
}
