package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.model.*
import ai.masaic.platform.api.model.PlatformAgent
import ai.masaic.platform.api.registry.functions.FunctionCreate
import ai.masaic.platform.api.registry.functions.FunctionRegistryService
import ai.masaic.platform.api.repository.MockFunctionRepository
import ai.masaic.platform.api.repository.MocksRepository
import ai.masaic.platform.api.tools.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource

/**
 * Service responsible for bootstrapping pre-built agents from JSON configuration
 * during application startup.
 */
open class AgentBootstrapService(
    private val agentService: AgentService,
    private val platformMcpService: PlatformMcpService,
    private val mockFunctionRepository: MockFunctionRepository,
    private val mocksRepository: MocksRepository,
    private val functionRegistryService: FunctionRegistryService,
) {
    private val log = KotlinLogging.logger { }
    private val agentDefFileName: String = "bootstrap-agents.json"
    private val mockFunFileName: String = "bootstrap-mock-functions.json"
    private val mapper = jacksonObjectMapper()

    /**
     * Event listener triggered when the application is ready.
     * Initiates the agent bootstrapping process.
     */
    @EventListener(ApplicationReadyEvent::class)
    open suspend fun bootstrapAgentsOnStartup() {
        try {
            log.info("Starting agent bootstrapping process...")
            val agents = loadAgentDefinitions()
            val mockFunctions = loadMockFunctions()
            if (agents.isEmpty()) {
                log.warn("No agent definitions found in classpath resource: $agentDefFileName")
                return
            }

            // Check if we should skip bootstrapping entirely
            if (agentService.getAllAgents().isNotEmpty()) {
                log.info("Skipping agent bootstrapping because agent(s) exist in the store")
                return
            }

            val bootstrappedCount = bootstrapAgents(agents, mockFunctions)
            log.info("Agent bootstrapping completed.")
        } catch (e: Exception) {
            log.error("Failed to bootstrap agents: ${e.message}", e)
        }
    }

    /**
     * Loads agent definitions from JSON file in classpath.
     *
     * @return List of PlatformAgent definitions
     */
    private suspend fun loadAgentDefinitions(): List<PlatformAgent> {
        val resource = ClassPathResource(agentDefFileName)
        if (!resource.exists()) {
            log.warn("Agent definitions file not found: $agentDefFileName")
            return emptyList()
        }

        val agents: List<PlatformAgent> = mapper.readValue(resource.inputStream.bufferedReader().use { it.readText() })
        log.info("Loaded ${agents.size} agent definitions from $agentDefFileName")
        return agents
    }

    /**
     * Loads mock functions from JSON file in classpath.
     *
     * @return List of PlatformAgent definitions
     */
    private suspend fun loadMockFunctions(): Map<String, BootstrapMockFunction> {
        val resource = ClassPathResource(mockFunFileName)
        if (!resource.exists()) {
            log.warn("Agent definitions file not found: $mockFunFileName")
            return emptyMap()
        }

        val functions: List<BootstrapMockFunctionDef> = mapper.readValue(resource.inputStream.bufferedReader().use { it.readText() })
        log.info("Loaded ${functions.size} functions from $mockFunFileName")
        val functionMap = mutableMapOf<String, BootstrapMockFunction>()
        functions.map { function ->
            val mockFunctionDef = MockFunctionDefinition(functionBody = function.function, outputSchem = "")
            val mocks = Mocks(refFunctionId = mockFunctionDef.id, mockJsons = function.mocks.map { mapper.writeValueAsString(it) })
            functionMap[function.function.name] = BootstrapMockFunction(function = mockFunctionDef, mocks = mocks)
        }
        return functionMap
    }

    /**
     * Bootstraps agents by checking if they exist and creating/updating them as configured.
     *
     * @param agents List of agents to bootstrap
     * @return Number of agents successfully bootstrapped
     */
    private suspend fun bootstrapAgents(
        agents: List<PlatformAgent>,
        mockFunctions: Map<String, BootstrapMockFunction>,
    ) {
        agents.forEach { agent ->
            try {
                var agentToSave = agent
                var possibleToBootstrap = true
                val agentTools = mutableListOf<Tool>()
                agentTools.addAll(agent.tools)
                agentTools.forEach { tool ->
                    when (tool) {
                        is FileSearchTool,
                        is AgenticSeachTool,
                        is FunctionTool,
                        -> {
                            log.info("${agent.name} is having tool ${tool.type} which is not supported. Skipping this agent.")
                            possibleToBootstrap = false
                        }

                        is MCPTool -> {
                            if (tool.serverUrl.endsWith("mock.masaic.ai/api/mcp")) {
                                val mocks: List<MockFunctionDefinition> =
                                    tool.allowedTools.mapNotNull { name ->
                                        val boot =
                                            mockFunctions[name] ?: run {
                                                log.info("${agent.name} has mock mcp function $name not defined. Skipping.")
                                                null
                                            } ?: return@mapNotNull null

                                        log.info("Saving mock function $name for ${agent.name}")
                                        val def = mockFunctionRepository.upsert(boot.function)
                                        mocksRepository.upsert(boot.mocks.copy(refFunctionId = def.id))
                                        def
                                    }

                                val mockServer = platformMcpService.createMockServer(CreateMockMcpServerRequest(tool.serverLabel, mocks.map { it.id }))
                                agentTools.remove(tool)
                                val mcpServer = MCPTool(type = "mcp", serverLabel = mockServer.serverLabel, serverUrl = mockServer.url).toMCPServerInfo()
                                agentTools += MCPTool(type = "mcp", serverLabel = mockServer.serverLabel, serverUrl = mockServer.url, allowedTools = mocks.map { it.functionBody.name })
                            }
                        }

                        is PyFunTool -> {
                            val pythonFunction = FunctionCreate(name = tool.functionDetails.name, description = tool.functionDetails.description, code = tool.code, inputSchema = tool.functionDetails.parameters)
                            functionRegistryService.createFunction(pythonFunction)
                            log.info("saving python function tool ${pythonFunction.name} for ${agent.name}")
                        }
                    }
                    agentToSave = agent.copy(tools = agentTools)
                }
                if (possibleToBootstrap) {
                    agentService.saveAgent(agentToSave, false)
                    log.info("Agent '${agent.name}' bootstrapped.")
                }
            } catch (e: Exception) {
                log.error("Failed to bootstrap agent '${agent.name}': ${e.message}", e)
            }
        }
    }
}

data class BootstrapMockFunctionDef(
    val function: FunctionBody,
    val mocks: List<Map<String, Any>>,
)

data class BootstrapMockFunction(
    val function: MockFunctionDefinition,
    val mocks: Mocks,
)
