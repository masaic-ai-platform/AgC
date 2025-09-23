package ai.masaic.platform.usecases.api.config

import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.repository.McpMockServerRepository
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.usecases.api.service.AtomTemporalWorkflowService
import ai.masaic.platform.usecases.api.service.AtomWorkflowService
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
        atomWorkflowService: AtomWorkflowService,
        temporalService: AtomTemporalWorkflowService,
    ) = UseCasesMcpClientFactory(mcpMockServerRepository, mockFunctionRepository, mocksRepository, modelSettings, modelService, atomWorkflowService, temporalService)
}
