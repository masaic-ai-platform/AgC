package ai.masaic.openresponses.tool.mcp

import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpClientTransport
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration

/**
 * Factory for creating MCP clients using the official SDK.
 * Handles dynamic transport selection (SSE->HTTP fallback) and authentication via headers.
 */
open class McpWebFluxClientFactory : McpClientFactory {

    private val log = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()
    
    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val REQUEST_TIMEOUT_SECONDS = 120L
    }

    override suspend fun init(
        serverName: String,
        url: String,
        headers: Map<String, String>
    ): ai.masaic.openresponses.tool.mcp.McpClient {
        
        log.info("Initializing MCP SDK client for server '$serverName' at: $url")

        // Create request builder with headers
        val requestBuilder = HttpRequest.newBuilder()
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json, text/event-stream")
        headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val transport = createTransport(url, requestBuilder)
        
        // Create MCP async client
        val mcpClient = McpClient.async(transport)
            .requestTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .initializationTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .loggingConsumer { notification ->
                log.info ("MCP server log [{}]: {}", serverName, notification)
                Mono.empty()
            }
            .progressConsumer { notification ->
                log.info("MCP progress [{}]: {}", serverName, notification)
                Mono.empty()
            }
            .build()
        
        // Create our wrapper client
        val sdkBackedClient = SdkBackedMcpClient(mcpClient, serverName)
        
        // Initialize the connection
        sdkBackedClient.initialize()
        
        return sdkBackedClient
    }

    override suspend fun init(
        serverName: String,
        mcpServer: MCPServer
    ): ai.masaic.openresponses.tool.mcp.McpClient {
        // MCPServer is for STDIO servers, not HTTP - this method shouldn't be used for our HTTP implementation
        throw UnsupportedOperationException("MCPServer (STDIO) not supported by HTTP MCP client factory. Use init(serverName, url, headers) instead.")
    }

    private fun createTransport(url: String, requestBuilder: HttpRequest.Builder,): McpClientTransport {
        val uri = URI(url)
        val path = uri.rawPath ?: ""
        val baseUrl = URI(uri.scheme, uri.authority, null, null, null).toString()
        val streamableHttpTransport = HttpClientStreamableHttpTransport.builder(baseUrl).
        apply {
            if(path.isNotEmpty())
                endpoint(path)
            else
                endpoint(baseUrl)
        }
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .requestBuilder(requestBuilder)
        .build()
        return streamableHttpTransport
    }
}
