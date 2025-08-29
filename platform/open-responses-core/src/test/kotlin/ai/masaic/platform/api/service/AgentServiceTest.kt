package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.tool.ToolService
import ai.masaic.platform.api.controller.*
import ai.masaic.platform.api.model.AgentClass
import ai.masaic.platform.api.model.PlatformAgent
import ai.masaic.platform.api.model.PlatformAgentMeta
import ai.masaic.platform.api.registry.functions.FunctionRegistryService
import ai.masaic.platform.api.repository.AgentRepository
import ai.masaic.platform.api.service.AgentService
import ai.masaic.platform.api.tools.PlatformMcpService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AgentServiceTest {
    private lateinit var agentService: AgentService
    private lateinit var agentRepository: AgentRepository
    private lateinit var funRegService: FunctionRegistryService
    private lateinit var platformMcpService: PlatformMcpService
    private lateinit var toolService: ToolService

    @BeforeEach
    fun setUp() {
        agentRepository = mockk()
        funRegService = mockk()
        platformMcpService = mockk()
        toolService = mockk()
        agentService = AgentService(agentRepository, funRegService, platformMcpService, toolService)
    }

    @Test
    fun `getAgent should return built-in agent when name matches`() =
        runTest {
            // Given
            val agentName = "Masaic-Mocky"

            // When
            val result = agentService.getAgent(agentName)

            // Then
            assertNotNull(result)
            result?.let { agent ->
                assertEquals("Masaic-Mocky", agent.name)
                assertEquals("Mocky: Expert in making mock MCP servers quickly", agent.description)
                assertEquals("Hi, this is Mocky. Let me know the quick mock functions you would like to create.", agent.greetingMessage)
                assertTrue(agent.systemPrompt.contains("Function Requirement Gathering"))
                assertEquals(5, agent.tools.size)
                assertEquals("system", agent.kind.kind)
            }
        }

    @Test
    fun `getAgent should return built-in ModelTestAgent when name matches`() =
        runTest {
            // Given
            val agentName = "ModelTestAgent"

            // When
            val result = agentService.getAgent(agentName)

            // Then
            assertNotNull(result)
            result?.let { agent ->
                assertEquals("ModelTestAgent", agent.name)
                assertEquals("This agent tests compatibility of model with platform", agent.description)
                assertEquals("Hi, let me test Model with query: \"Tell me the weather of San Francisco\"", agent.greetingMessage)
                assertTrue(agent.systemPrompt.contains("Weather Information Provider"))
                assertEquals(1, agent.tools.size)
                assertEquals("system", agent.kind.kind)
            }
        }

    @Test
    fun `getAgent should return built-in AgentBuilder when name matches`() =
        runTest {
            // Given
            val agentName = "Agent-Builder"
            every { runBlocking { platformMcpService.getAllMockServers() } } returns emptyList()
            every { runBlocking { funRegService.getAllAvailableFunctions(false) } } returns emptyList()
            // When
            val result = agentService.getAgent(agentName)
            // Then
            assertNotNull(result)
            result?.let { agent ->
                assertEquals("AgC0", agent.name)
                assertEquals("system", agent.kind.kind)
            }
        }

    @Test
    fun `getAllAgents should return only persisted agents (not SYSTEM agents)`() =
        runTest {
            // Given
            val persistedAgent =
                PlatformAgentMeta(
                    name = "CustomAgent",
                    description = "A custom agent",
                    systemPrompt = "Custom system prompt",
                    kind = AgentClass(AgentClass.OTHER),
                )
            coEvery { agentRepository.findAll() } returns listOf(persistedAgent)

            // When
            val result = agentService.getAllAgents()

            // Then
            assertEquals(1, result.size) // Only 1 persisted agent, no SYSTEM agents
            assertTrue(result.any { it.name == "CustomAgent" })
            assertFalse(result.any { it.kind.kind == "system" })
        }

    @Test
    fun `saveAgent should throw exception when agent name already exists`() =
        runTest {
            // Given
            val agent =
                PlatformAgent(
                    name = "ExistingAgent",
                    description = "A test agent",
                    systemPrompt = "Test system prompt",
                    kind = AgentClass(AgentClass.OTHER),
                )

            val existingAgent =
                PlatformAgentMeta(
                    name = "existingAgent",
                    description = "Existing agent",
                    systemPrompt = "Existing prompt",
                    kind = AgentClass(AgentClass.OTHER),
                )

            coEvery { agentRepository.findByName("existingAgent") } returns existingAgent

            // When & Then
            assertThrows<ResponseProcessingException> {
                agentService.saveAgent(agent, false)
            }
        }
}
