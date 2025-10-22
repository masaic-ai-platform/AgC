package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.tool.mcp.oauth.MCPOAuthService
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.spec.McpClientTransport
import mu.KotlinLogging
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration

/**
 * Factory for creating MCP clients using the official SDK.
 * Handles dynamic transport selection (SSE->HTTP fallback) and authentication via headers.
 */
open class McpWebFluxClientFactory(
    private val mcpoAuthService: MCPOAuthService,
) : McpClientFactory {
    private val log = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val REQUEST_TIMEOUT_SECONDS = 120L
    }

    override suspend fun init(
        serverName: String,
        url: String,
        headers: Map<String, String>,
    ): ai.masaic.openresponses.tool.mcp.McpClient {
        log.info("Initializing MCP SDK client for server '$serverName' at: $url")

        val transport = createTransport(url, headers)
        
        // Create MCP async client
        val mcpClient =
            McpClient
                .async(transport)
                .requestTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .initializationTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .loggingConsumer { notification ->
                    log.info("MCP server log [{}]: {}", serverName, notification)
                    Mono.empty()
                }.progressConsumer { notification ->
                    log.info("MCP progress [{}]: {}", serverName, notification)
                    Mono.empty()
                }.build()
        
        // Create our wrapper client
        val sdkBackedClient = SdkBackedMcpClient(mcpClient, serverName)
        
        // Initialize the connection
        sdkBackedClient.initialize()
        
        return sdkBackedClient
    }

    override suspend fun init(
        serverName: String,
        mcpServer: MCPServer,
    ): ai.masaic.openresponses.tool.mcp.McpClient {
        // MCPServer is for STDIO servers, not HTTP - this method shouldn't be used for our HTTP implementation
        throw UnsupportedOperationException("MCPServer (STDIO) not supported by HTTP MCP client factory. Use init(serverName, url, headers) instead.")
    }

    private suspend fun createTransport(
        url: String,
        headers: Map<String, String>,
    ): McpClientTransport {
        val uri = URI(url)
        val path = uri.rawPath ?: ""
        val baseUrl = URI(uri.scheme, uri.authority, null, null, null).toString()

        val webClientBuilder =
            WebClient
                .builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json; charset=utf-8")
                .defaultHeader("Accept", "application/json, text/event-stream")

        headers.forEach {
            webClientBuilder.defaultHeader(it.key, it.value)
        }

        val transport =
            WebClientStreamableHttpTransport
                .builder(webClientBuilder)
                .jsonMapper(JacksonMcpJsonMapper(objectMapper))
                .apply {
                    if (path.isNotEmpty()) {
                        endpoint(path)
                    } else {
                        endpoint(baseUrl)
                    }
                }.build()

        return transport
    }
}
