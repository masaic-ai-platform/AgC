package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.tool.NativeToolRegistry
import ai.masaic.openresponses.tool.mcp.*
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
        toolStorage: ToolRegistryStorage,
    ) = NativeToolRegistry(objectMapper, responseStore, toolStorage)

    @Bean
    @ConditionalOnMissingBean(McpClientStore::class)
    fun mcpClientStore() = CaffeineMcpClientStore()
}
