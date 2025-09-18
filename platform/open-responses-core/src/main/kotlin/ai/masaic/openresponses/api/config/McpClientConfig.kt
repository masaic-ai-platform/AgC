package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.tool.NativeToolRegistry
import ai.masaic.openresponses.tool.mcp.McpClientFactory
import ai.masaic.openresponses.tool.mcp.McpWebFluxClientFactory
import ai.masaic.openresponses.tool.mcp.SimpleMcpClientFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!platform")
class McpClientConfig {
    
    @Bean
//    @ConditionalOnProperty(value = ["mcp.client.implementation"], havingValue = "sdk", matchIfMissing = true)
    fun mcpClientFactory(): McpClientFactory = McpWebFluxClientFactory()
    
//    @Bean
//    @ConditionalOnProperty(value = ["mcp.client.implementation"], havingValue = "legacy")
    fun legacyMcpClientFactory(): McpClientFactory = SimpleMcpClientFactory()

    @Bean
    fun nativeToolRegistry(
        objectMapper: ObjectMapper,
        responseStore: ResponseStore,
    ) = NativeToolRegistry(objectMapper, responseStore)
}
