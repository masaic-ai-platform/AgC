package ai.masaic.platform.api.config

import ai.masaic.platform.api.service.AgcMcpServerToolsService
import ai.masaic.platform.api.service.AgentService
import ai.masaic.platform.api.service.AskAgentService
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpStatelessAsyncServer
import io.modelcontextprotocol.server.transport.WebFluxStatelessServerTransport
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * For any confusion/reference check in implementation, refer: https://modelcontextprotocol.io/sdk/java/mcp-server#async-2
 */
@ConditionalOnProperty(name = ["platform.deployment.mcp-server.enabled"], havingValue = "true")
@Configuration
class AgCMcpServerConfig(
    private val env: Environment,
) {
    @Bean
    fun mcpAuthFilter() = McpAuthFilter(env)

    @Bean
    fun webFluxStatelessServerTransport(mapper: ObjectMapper?): WebFluxStatelessServerTransport =
        WebFluxStatelessServerTransport
            .builder()
            .messageEndpoint("/mcp")
            .build()

    @Bean
    fun mcpRouterFunction(
        transportProvider: WebFluxStatelessServerTransport,
    ): RouterFunction<ServerResponse> {
        @Suppress("UNCHECKED_CAST")
        return transportProvider.routerFunction as RouterFunction<ServerResponse>
    }

    @Bean
    fun prepareASyncServerBuilder(
        agcMcpServerToolsService: AgcMcpServerToolsService,
        statelessServerTransport: WebFluxStatelessServerTransport?,
    ): McpStatelessAsyncServer {
        val toolSpecs = runBlocking { agcMcpServerToolsService.provideTools() }
        val capabilities =
            McpSchema.ServerCapabilities
                .builder()
                .tools(true)
                .resources(false, true)
                .prompts(false)
                .logging()
                .completions()
                .build()

        return McpServer
            .async(statelessServerTransport)
            .serverInfo("AgC-MCP-Server", "1.0.0")
            .capabilities(capabilities)
            .tools(toolSpecs)
            .build()
    }

    @Bean
    fun agcMcpServerToolsService(
        askAgentService: AskAgentService,
        agentService: AgentService,
    ) = AgcMcpServerToolsService(askAgentService, agentService)
}

class McpAuthFilter(
    private val env: Environment,
) : WebFilter {
    private val validApiKeys: Set<String> =
        env
            .getProperty("platform.deployment.mcp-server.validApiKeys")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: throw IllegalStateException(
                "platform.deployment.mcp-server.validApiKeys cannot be left blank or empty.",
            )

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val req = exchange.request
        val path = req.path.value()

        // guard only the MCP endpoint
        if (!path.startsWith("/mcp")) {
            return chain.filter(exchange)
        }

        // allow CORS preflight
        if (req.method == HttpMethod.OPTIONS) {
            return chain.filter(exchange)
        }

        val auth = req.headers.getFirst(HttpHeaders.AUTHORIZATION)
        val token =
            auth
                ?.takeIf { it.startsWith("Bearer ") }
                ?.removePrefix("Bearer ")
                ?.trim()

        if (token != null && token in validApiKeys) {
            return chain.filter(exchange)
        }
        val resp = exchange.response
        resp.statusCode = HttpStatus.UNAUTHORIZED
        resp.headers.add(
            HttpHeaders.WWW_AUTHENTICATE,
            // RFC 6750 format
            """Bearer realm="AgC-MCP", error="invalid_token", error_description="Missing or invalid API key"""",
        )
        return resp.setComplete()
    }
}
