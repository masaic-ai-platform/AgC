package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.ToolHosting
import ai.masaic.openresponses.tool.ToolParamsAccessor
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import dev.langchain4j.mcp.client.Converter
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import java.util.concurrent.CompletionException

/**
 * MCP client implementation using the official Model Context Protocol SDK.
 * Replaces the custom McpSyncClient with proper transport abstraction and capabilities.
 */
class SdkBackedMcpClient(
    private val mcpClient: McpAsyncClient,
    private val serverName: String
) : McpClient {
    
    private val log = KotlinLogging.logger {}
    private val mapper = jacksonObjectMapper()
    
    /**
     * Initialize the MCP connection.
     * The transport and authentication are already configured at construction time.
     */
    suspend fun initialize() {
        try {
            log.info("Starting MCP client initialization for server: $serverName")
            mcpClient.initialize().awaitSingle()
            log.info("MCP SDK client initialized successfully for server: $serverName")
        } catch (e: Exception) {
            log.error("Failed to initialize MCP client for server: $serverName", e)
            log.error("Exception details: ${e.javaClass.simpleName} - ${e.message}")
            e.cause?.let { cause ->
                log.error("Caused by: ${cause.javaClass.simpleName} - ${cause.message}")
                throw mapException(cause, "initialization")
            } ?:throw mapException(e, "initialization")
        }
    }

    override suspend fun listTools(mcpServerInfo: MCPServerInfo): List<McpToolDefinition> {
            val result = mcpClient.listTools().awaitSingle()
            return result.tools.mapNotNull { tool ->
                val params = tool.inputSchema?.let {
                    val schemaNode = mapper.readTree(mapper.writeValueAsString(it))
                    try {
                        Converter.toJsonObjectSchema(schemaNode)
                    }catch (ex: McpToolsInputSchemaParsingException) {
                        log.error { ex }
                        null
                    }
                } ?: JsonObjectSchema.builder().build()
                params?.let {
                    McpToolDefinition(
                        hosting = ToolHosting.REMOTE,
                        name = mcpServerInfo.qualifiedToolName(tool.name()),
                        description = tool.description ?: tool.name,
                        parameters = params,
                        serverInfo = mcpServerInfo
                    )
                }
            }
    }

    override suspend fun executeTool(
        tool: ToolDefinition,
        arguments: String,
        paramsAccessor: ToolParamsAccessor?,
        openAIClient: OpenAIClient?,
        headers: Map<String, String>
    ): String? {
        return try {
            // Parse arguments JSON to Map
            val argsMap: Map<String, Any> = if (arguments.isNotBlank()) {
                mapper.readValue(arguments)
            } else {
                emptyMap()
            }

            val request = McpSchema.CallToolRequest(tool.name, argsMap)
            val result = mcpClient.callTool(request).awaitSingle()
            mapper.writeValueAsString(Converter.extractResult(result))

        } catch (e: Exception) {
            log.error("Failed to execute tool '${tool.name}' for server: $serverName", e)
            throw mapException(e, "executeTool")
        }
    }

    override suspend fun close() {
        try {
            mcpClient.closeGracefully().awaitSingle()
            log.info("MCP SDK client closed gracefully for server: $serverName")
        } catch (e: Exception) {
            log.warn("Error while closing MCP client for server: $serverName", e)
            // Don't throw on close errors
        }
    }

    /**
     * Map SDK exceptions to existing application exception types for compatibility.
     */
    private fun mapException(e: Throwable, operation: String): Exception {
        return when (e) {
            is TimeoutCancellationException -> {
                McpException("MCP $operation timed out for server: $serverName", e)
            }
            is CompletionException -> {
                when (val cause = e.cause) {
                    is TimeoutCancellationException -> {
                        McpException("MCP $operation timed out for server: $serverName", e)
                    }
                    else -> {
                        McpException("MCP $operation failed for server: $serverName - ${cause?.message ?: e.message}", e)
                    }
                }
            }
            else -> {
                McpException("MCP $operation failed for server: $serverName - ${e.message}", e)
            }
        }
    }
}
