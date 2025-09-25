package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.tool.NativeToolRegistry
import ai.masaic.openresponses.tool.mcp.McpClientFactory
import ai.masaic.openresponses.tool.mcp.McpWebFluxClientFactory
import ai.masaic.openresponses.tool.mcp.oauth.MCPOAuthService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!platform")
class McpClientConfig {
    @Bean
    fun mcpClientFactory(oauthService: MCPOAuthService): McpClientFactory = McpWebFluxClientFactory(oauthService)

    @Bean
    fun nativeToolRegistry(
        objectMapper: ObjectMapper,
        responseStore: ResponseStore,
    ) = NativeToolRegistry(objectMapper, responseStore)
}
