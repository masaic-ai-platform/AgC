package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.tool.ToolService
import ai.masaic.platform.api.controller.*
import ai.masaic.platform.api.registry.functions.FunctionRegistryService
import ai.masaic.platform.api.repository.AgentRepository
import ai.masaic.platform.api.service.AgentService
import ai.masaic.platform.api.tools.PlatformMcpService
import io.mockk.*
import io.mockk.coVerify
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
            val agentName = "masaic-mocky"

            // When
            val result = agentService.getAgent(agentName)

            // Then
            assertNotNull(result)
            result?.let { agent ->
                assertEquals("masaic-mocky", agent.name)
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
            val agentName = "modeltestagent"

            // When
            val result = agentService.getAgent(agentName)

            // Then
            assertNotNull(result)
            result?.let { agent ->
                assertEquals("modeltestagent", agent.name)
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
            val agentName = "agent-builder"
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
    fun `getAgent should return null for unknown built-in agent`() =
        runTest {
            // Given
            val agentName = "UnknownAgent"
            coEvery { agentRepository.findByName(agentName) } returns null

            // When
            val result = agentService.getAgent(agentName)

            // Then
            assertNull(result)
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
    fun `deleteAgent should return false for built-in agents`() =
        runTest {
            // Given
            val agentName = "masaic-mocky"

            // When
            val result = agentService.deleteAgent(agentName)

            // Then
            assertFalse(result)
        }

    @Test
    fun `deleteAgent should return true for persisted agents`() =
        runTest {
            // Given
            val agentName = "CustomAgent"
            coEvery { agentRepository.deleteByName(agentName) } returns true

            // When
            val result = agentService.deleteAgent(agentName)

            // Then
            assertTrue(result)
        }

    @Test
    fun `saveAgent should save agent and register PyFunTools`() =
        runTest {
            // Given
            val agent =
                PlatformAgent(
                    name = "TestAgent",
                    description = "A test agent",
                    systemPrompt = "Test system prompt",
                    tools =
                        listOf(
                            MCPTool(
                                type = "mcp",
                                serverLabel = "test-server",
                                serverUrl = "http://test.com",
                            ),
                            FileSearchTool(
                                type = "file_search",
                                vectorStoreIds = listOf("vs1", "vs2"),
                                modelInfo = ModelInfo(bearerToken = "token", model = "gpt-4"),
                            ),
                        ),
                    kind = AgentClass(AgentClass.OTHER),
                )

            coEvery { agentRepository.findByName("TestAgent") } returns null
            coEvery { agentRepository.upsert(any()) } returns
                PlatformAgentMeta(
                    name = "TestAgent",
                    description = "A test agent",
                    systemPrompt = "Test system prompt",
                    kind = AgentClass(AgentClass.OTHER),
                )

            // When
            agentService.saveAgent(agent, false)

            // Then
            coVerify { 
                agentRepository.findByName("TestAgent")
                agentRepository.upsert(any())
            }
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
                    name = "ExistingAgent",
                    description = "Existing agent",
                    systemPrompt = "Existing prompt",
                    kind = AgentClass(AgentClass.OTHER),
                )

            coEvery { agentRepository.findByName("ExistingAgent") } returns existingAgent

            // When & Then
            assertThrows<ResponseProcessingException> {
                agentService.saveAgent(agent, false)
            }
        }
}
