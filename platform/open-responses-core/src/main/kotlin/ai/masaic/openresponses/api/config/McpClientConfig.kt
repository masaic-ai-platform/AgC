package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.tool.NativeToolRegistry
import ai.masaic.openresponses.tool.mcp.CaffeineMcpClientStore
import ai.masaic.openresponses.tool.mcp.McpClientFactory
import ai.masaic.openresponses.tool.mcp.McpClientStore
import ai.masaic.openresponses.tool.mcp.McpWebFluxClientFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpClientConfig {
    @Bean
    @ConditionalOnMissingBean(McpClientFactory::class)
    fun mcpClientFactory(): McpClientFactory = McpWebFluxClientFactory()

    @Bean
    @ConditionalOnMissingBean
    fun nativeToolRegistry(
        objectMapper: ObjectMapper,
        responseStore: ResponseStore,
    ) = NativeToolRegistry(objectMapper, responseStore)

    @Bean
    @ConditionalOnMissingBean(McpClientStore::class)
    fun mcpClientStore() = CaffeineMcpClientStore()
}
