package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.ToolHosting
import ai.masaic.openresponses.tool.ToolParamsAccessor
import com.openai.client.OpenAIClient
import org.slf4j.LoggerFactory
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component

/**
 * Component responsible for executing MCP tools.
 *
 * This component manages connections to MCP servers and provides functionality
 * to execute tools on these servers.
 */
@Component
class MCPToolExecutor(
    private val mcpClientFactory: McpClientFactory,
    private val mcpToolRegistry: MCPToolRegistry,
    private val mcpClientStore: McpClientStore,
) {
    private val log = LoggerFactory.getLogger(MCPToolExecutor::class.java)

    /**
     * Connects to an MCP server based on the provided configuration.
     *
     * @param serverName Name of the server to connect to
     * @param mcpServer Server configuration
     * @return McpClient instance connected to the server
     */
    suspend fun connectServer(
        serverName: String,
        mcpServer: MCPServer,
    ): McpClient {
        val mcpClient = mcpClientFactory.init(serverName, mcpServer)
        mcpClientStore.add(serverName, mcpClient)
        return mcpClient
    }

    suspend fun addMcpClient(
        serverName: String,
        mcpClient: McpClient,
    ) {
        mcpClientStore.add(serverName, mcpClient)
    }

    suspend fun initMcp(mcpTool: MCPTool): List<McpToolDefinition> {
        val mcpClient = mcpClientFactory.init(mcpTool.serverLabel, mcpTool.serverUrl, mcpTool.headers)
        val availableTools = mcpClient.listTools(MCPServerInfo(mcpTool.serverLabel, mcpTool.serverUrl, mcpTool.headers))
        availableTools.forEach {
            mcpToolRegistry.addTool(it)
        }
        mcpToolRegistry.addMcpServer(MCPServerInfo(mcpTool.serverLabel, mcpTool.serverUrl, mcpTool.headers, availableTools.map { it.name }))
        addMcpClient(mcpTool.toMCPServerInfo().serverIdentifier(), mcpClient)
        return availableTools
    }

    /**
     * Executes a tool with the provided arguments.
     *
     * @param tool The tool definition to execute
     * @param arguments JSON string containing arguments for the tool
     * @return Result of the tool execution as a string, or null if the tool can't be executed
     */
    suspend fun executeTool(
        tool: ToolDefinition,
        arguments: String,
        paramsAccessor: ToolParamsAccessor?,
        openAIClient: OpenAIClient?,
        eventEmitter: ((ServerSentEvent<String>) -> Unit)?,
    ): String? {
        val mcpToolDef = tool as McpToolDefinition
        var serverId = mcpToolDef.serverInfo.id
        var toolName = mcpToolDef.name
        var mcpTool: MCPTool? = null
        if (mcpToolDef.hosting == ToolHosting.REMOTE) {
            serverId = mcpToolDef.serverInfo.serverIdentifier()
            toolName = mcpToolDef.serverInfo.unQualifiedToolName(mcpToolDef.name)
            mcpTool = MCPTool(type = "mcp", serverLabel = mcpToolDef.serverInfo.id, serverUrl = mcpToolDef.serverInfo.url, headers = mcpToolDef.serverInfo.headers, allowedTools = mcpToolDef.serverInfo.tools)
        }

        val mcpClient =
            mcpClientStore.getIfPresent(serverId) ?: run {
                mcpTool?.let { initMcp(it) }
                mcpClientStore.getIfPresent(serverId)
            } ?: return null
        return mcpClient.executeTool(tool.copy(name = toolName), arguments, paramsAccessor, openAIClient, headers = mcpToolDef.serverInfo.headers, eventEmitter)
    }

    /**
     * Shuts down all MCP clients, releasing resources.
     */
    suspend fun shutdown() {
        mcpClientStore.asMap().forEach { (_, mcpClient) ->
            mcpClient.close()
        }
    }
}

/**
 * Component responsible for managing MCP tool definitions.
 *
 * This registry maintains a collection of tool definitions and provides
 * methods to register, find, and clean up tools.
 */
@Component
class MCPToolRegistry(
    private val toolStorage: ToolRegistryStorage,
    private val serverStorage: McpServerInfoRegistryStorage,
) {
    private val log = LoggerFactory.getLogger(MCPToolRegistry::class.java)

    /**
     * Registers MCP tools from the given client.
     *
     * @param serverName Name of the server hosting the tools
     * @param mcpClient Client connected to the server
     */
    suspend fun registerMCPTools(
        serverName: String,
        mcpClient: McpClient,
    ) {
        val mcpTools = mcpClient.listTools(MCPServerInfo(serverName))
        mcpTools.forEach { addTool(it) }
    }

    /**
     * Adds a tool to the registry.
     *
     * @param tool Tool definition to add
     */
    suspend fun addTool(tool: ToolDefinition) {
        toolStorage.add<McpToolDefinition>(tool as McpToolDefinition)
        log.debug("Added tool '${tool.name}' to registry")
    }

    suspend fun invalidateTool(tool: McpToolDefinition) {
        toolStorage.remove<McpToolDefinition>(tool.name)
        serverStorage.remove(tool.serverInfo.serverIdentifier())
        log.debug("Invalidated tool '${tool.name}' from registry")
    }

    suspend fun addMcpServer(mcpServerInfo: MCPServerInfo) {
        serverStorage.add(mcpServerInfo)
    }

    suspend fun removeMcpServer(mcpServerInfo: MCPServerInfo) {
        serverStorage.remove(mcpServerInfo.serverIdentifier())
    }

    /**
     * Finds a tool by name.
     *
     * @param name Name of the tool to find
     * @return Tool definition if found, null otherwise
     */
    suspend fun findByName(name: String): ToolDefinition? = toolStorage.get<McpToolDefinition>(name)

    suspend fun findServerById(id: String): MCPServerInfo? = serverStorage.get(id)
}
