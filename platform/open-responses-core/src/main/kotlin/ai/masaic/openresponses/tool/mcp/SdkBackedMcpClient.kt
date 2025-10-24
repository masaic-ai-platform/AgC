package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.ToolHosting
import ai.masaic.openresponses.tool.ToolParamsAccessor
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.TextContent
import io.modelcontextprotocol.spec.McpTransportException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * MCP client implementation using the official Model Context Protocol SDK.
 * Replaces the custom McpSyncClient with proper transport abstraction and capabilities.
 */
class SdkBackedMcpClient(
    private val mcpClient: McpAsyncClient,
    private val serverName: String,
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
            throw mapException(e, "initialize")
        }
    }

    override suspend fun listTools(mcpServerInfo: MCPServerInfo): List<McpToolDefinition> {
        try {
            val result = mcpClient.listTools().awaitSingle()
            return result.tools.mapNotNull { tool ->
                val params: MutableMap<String, Any> = tool.inputSchema?.let { mapper.convertValue(tool.inputSchema) } ?: mutableMapOf()
                McpToolDefinition(
                    hosting = ToolHosting.REMOTE,
                    name = mcpServerInfo.qualifiedToolName(tool.name()),
                    description = tool.description ?: tool.name,
                    parameters = params,
                    serverInfo = mcpServerInfo,
                )
            }
        } catch (e: Exception) {
            throw mapException(e, "listTools")
        }
    }

    override suspend fun executeTool(
        tool: ToolDefinition,
        arguments: String,
        paramsAccessor: ToolParamsAccessor?,
        openAIClient: OpenAIClient?,
        headers: Map<String, String>,
        eventEmitter: ((ServerSentEvent<String>) -> Unit)?,
    ): String? =
        try {
            // Parse arguments JSON to Map
            val argsMap: Map<String, Any> =
                if (arguments.isNotBlank()) {
                    mapper.readValue(arguments)
                } else {
                    emptyMap()
                }

            val request = McpSchema.CallToolRequest(tool.name, argsMap)
            val result = mcpClient.callTool(request).awaitSingle()
            mapper.writeValueAsString(extractResult(result))
        } catch (e: Exception) {
            log.error("Failed to execute tool '${tool.name}' for server: $serverName", e)
            throw mapException(e, "executeTool")
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
    private fun mapException(
        e: Throwable,
        operation: String,
    ): Throwable {
        return when (e) {
            is TimeoutCancellationException -> {
                val errorMessage = "MCP $operation timed out for server: $serverName"
                log.error { errorMessage }
                McpException(errorMessage, e)
            }
            else -> {
                val secondLevelCause = e.cause
                if (secondLevelCause is McpTransportException) {
                    if (secondLevelCause.cause != null && secondLevelCause.cause is WebClientResponseException) {
                        val webClientResponseException = secondLevelCause.cause as WebClientResponseException
                        val errorMessage = "MCP $operation failed for server: $serverName - statusCode=${webClientResponseException.statusCode}, response=${webClientResponseException.responseBodyAsString}, authHeader=${webClientResponseException.headers["WWW-Authenticate"]}"
                        log.error { errorMessage }
                        if (webClientResponseException.statusCode.value() == 401) {
                            return McpUnAuthorizedException(errorMessage)
                        }
                    }
                }
                val errorMessage = "MCP $operation failed for server: $serverName - ${e.message}"
                log.error { errorMessage }
                McpException(errorMessage, e)
            }
        }
    }

    private fun extractResult(result: McpSchema.CallToolResult): CallToolResponse =
        try {
            // Extract content from the result
            val content =
                if (result.content.isNotEmpty()) {
                    result.content.joinToString("\n") { contentItem ->
                        when (contentItem) {
                            is TextContent -> contentItem.text
                            else -> contentItem.toString()
                        }
                    }
                } else {
                    "No content returned returned by the tool."
                }

            // Check if this is an error result
            val isError = result.isError ?: false

            // Format error content if needed
            val finalContent =
                if (isError) {
                    "There was an error executing the tool. The tool returned: $content"
                } else {
                    content
                }

            CallToolResponse(isError, finalContent)
        } catch (ex: Exception) {
            log.warn("Error occurred while extracting CallToolResult: ${ex.message}", ex)
            CallToolResponse(true, "There was an error processing the tool result")
        }
}
