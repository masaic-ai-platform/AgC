package ai.masaic.openresponses.tool

import ai.masaic.openresponses.tool.mcp.MCPToolExecutor
import ai.masaic.openresponses.tool.mcp.MCPToolRegistry
import ai.masaic.openresponses.tool.mcp.McpWebFluxClientFactory
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ResourceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileSearchToolServiceTest {
    private lateinit var toolService: ToolService
    private lateinit var mcpToolRegistry: MCPToolRegistry
    private lateinit var mcpToolExecutor: MCPToolExecutor
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var nativeToolRegistry: NativeToolRegistry
    private lateinit var mcpClientFactory: McpWebFluxClientFactory

    @BeforeEach
    fun setUp() {
        mcpToolRegistry = mockk(relaxed = true)
        mcpToolExecutor = mockk(relaxed = true)
        resourceLoader = mockk(relaxed = true)
        nativeToolRegistry = mockk(relaxed = true)

        toolService =
            ToolService(
                mcpToolRegistry,
                mcpToolExecutor,
                resourceLoader,
                nativeToolRegistry,
                ObjectMapper(),
                NoOpPlugableToolAdapter(),
            )
    }

    @Test
    fun `getAvailableTool should return file_search tool`() =
        runBlocking {
            // Given
            val toolDefinition =
                NativeToolDefinition(
                    id = "file_search_id",
                    name = "file_search",
                    description = "Search through vector stores for relevant file content",
                    parameters =
                        mutableMapOf(
                            "type" to "object",
                            "properties" to
                                mapOf(
                                    "query" to
                                        mapOf(
                                            "type" to "string",
                                            "description" to "The search query",
                                        ),
                                ),
                            "required" to listOf("query"),
                        ),
                )

            coEvery { nativeToolRegistry.findByName("file_search") } returns toolDefinition
            coEvery { mcpToolRegistry.findByName(any()) } returns null

            // When
            val result = toolService.getAvailableTool("file_search")

            // Then
            assertNotNull(result)
            assertEquals("file_search_id", result.id)
            assertEquals("file_search", result.name)
            assertEquals("Search through vector stores for relevant file content", result.description)

            coVerify { nativeToolRegistry.findByName("file_search") }
        }

    @Test
    fun `getFunctionTool should return file_search as FunctionTool`() =
        runBlocking {
            // Given
            val toolDefinition =
                NativeToolDefinition(
                    id = "file_search_id",
                    name = "file_search",
                    description = "Search through vector stores for relevant file content",
                    parameters =
                        mutableMapOf(
                            "type" to "object",
                            "properties" to
                                mapOf(
                                    "query" to
                                        mapOf(
                                            "type" to "string",
                                            "description" to "The search query",
                                        ),
                                ),
                            "required" to listOf("query"),
                        ),
                )

            coEvery { nativeToolRegistry.findByName("file_search") } returns toolDefinition
            coEvery { mcpToolRegistry.findByName(any()) } returns null

            // When
            val result = toolService.getFunctionTool("file_search")

            // Then
            assertNotNull(result)
            assertEquals("file_search", result.name)
            assertEquals("Search through vector stores for relevant file content", result.description)

            // Check parameters
            assertTrue(result.parameters.containsKey("properties"))

            @Suppress("UNCHECKED_CAST")
            val properties = result.parameters["properties"] as Map<String, Any>
            assertTrue(properties.containsKey("query"))

            @Suppress("UNCHECKED_CAST")
            val queryProps = properties["query"] as Map<String, Any>
            assertEquals("string", queryProps["type"])
            assertEquals("The search query", queryProps["description"])

            assertTrue(result.parameters.containsKey("required"))
            @Suppress("UNCHECKED_CAST")
            val required = result.parameters["required"] as List<String>
            assertTrue(required.contains("query"))

            coVerify { nativeToolRegistry.findByName("file_search") }
        }
}
