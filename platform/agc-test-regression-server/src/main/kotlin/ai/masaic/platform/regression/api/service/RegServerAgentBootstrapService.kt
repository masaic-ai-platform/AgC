package ai.masaic.platform.regression.api.service

import ai.masaic.platform.api.registry.functions.FunctionRegistryService
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.service.AgentBootstrapService
import ai.masaic.platform.api.service.AgentService
import ai.masaic.platform.api.tools.PlatformMcpService
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RegServerAgentBootstrapService(
    private val agentService: AgentService,
    platformMcpService: PlatformMcpService,
    mockFunctionRepository: MockFunctionRepository,
    mocksRepository: MocksRepository,
    functionRegistryService: FunctionRegistryService,
) : AgentBootstrapService(
        agentService,
        platformMcpService,
        mockFunctionRepository,
        mocksRepository,
        functionRegistryService,
    ) {
    private val regTestSuiteAgent = RegTestSuiteAgent()

    @EventListener(ApplicationReadyEvent::class)
    override suspend fun bootstrapAgentsOnStartup() {
        super.bootstrapAgentsOnStartup()
        val regTestSuiteAgentDef = regTestSuiteAgent.getAgent()
        val regAgent = agentService.getAgent(regTestSuiteAgentDef.name)
        agentService.saveAgent(regTestSuiteAgent.getAgent(), regAgent != null)
    }
}
