package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.ToolParamsAccessor
import com.openai.client.OpenAIClient
import org.springframework.http.codec.ServerSentEvent

interface McpClientFactory {
    suspend fun init(
        serverName: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): McpClient

    suspend fun init(
        serverName: String,
        mcpServer: MCPServer,
    ): McpClient
}

interface McpClient {
    suspend fun listTools(mcpServerInfo: MCPServerInfo): List<McpToolDefinition>

    suspend fun executeTool(
        tool: ToolDefinition,
        arguments: String,
        paramsAccessor: ToolParamsAccessor?,
        openAIClient: OpenAIClient?,
        headers: Map<String, String>,
        eventEmitter: ((ServerSentEvent<String>) -> Unit)?,
    ): String?

    suspend fun close()
}

data class CallToolResponse(
    val isError: Boolean = false,
    val content: String,
)

class McpUnAuthorizedException(
    message: String,
) : RuntimeException(message)

class McpException(
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause)

class McpToolNotFoundException(
    message: String,
) : RuntimeException(message)

class McpToolsInputSchemaParsingException(
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause)
