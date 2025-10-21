package ai.masaic.platform.regression.api.service

import ai.masaic.platform.api.service.AgentService
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RegServerAgentBootstrapService(
    private val agentService: AgentService,
) {
    private val regTestSuiteAgent = RegTestSuiteAgent()

    @EventListener(ApplicationReadyEvent::class)
    suspend fun bootstrapAgentsOnStartup() {
        val regTestSuiteAgentDef = regTestSuiteAgent.getAgent()
        val regAgent = agentService.getAgent(regTestSuiteAgentDef.name)
        agentService.saveAgent(regTestSuiteAgent.getAgent(), regAgent != null)
    }
}
