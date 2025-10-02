package ai.masaic.platform.usecases.api.config

import ai.masaic.openresponses.tool.mcp.oauth.MCPOAuthService
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.repository.McpMockServerRepository
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.usecases.api.service.AtomTemporalWorkflowService
import ai.masaic.platform.usecases.api.tools.UseCasesMcpClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
class AgCUseCasesConfig {
    @Bean
    fun mcpClientFactory(
        mcpMockServerRepository: McpMockServerRepository,
        mockFunctionRepository: MockFunctionRepository,
        mocksRepository: MocksRepository,
        modelSettings: ModelSettings,
        @Lazy modelService: ModelService,
        temporalService: AtomTemporalWorkflowService,
        mcpoAuthService: MCPOAuthService,
    ) = UseCasesMcpClientFactory(mcpMockServerRepository, mockFunctionRepository, mocksRepository, modelSettings, modelService, mcpoAuthService, temporalService)
}
