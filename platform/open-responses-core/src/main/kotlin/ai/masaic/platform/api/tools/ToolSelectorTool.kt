package ai.masaic.platform.api.tools

import ai.masaic.openresponses.api.model.FunctionDetails
import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.api.model.PyFunTool
import ai.masaic.openresponses.tool.*
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.registry.functions.FunctionRegistryService
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.service.messages
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import org.springframework.http.codec.ServerSentEvent

class ToolSelectorTool(
    modelSettings: ModelSettings,
    modelService: ModelService,
    private val platformMcpService: PlatformMcpService,
    private val funRegService: FunctionRegistryService,
    private val toolService: ToolService
) : ModelDepPlatformNativeTool(PlatformToolsNames.TOOl_SELECTOR_TOOL, modelService, modelSettings) {
    override fun provideToolDef(): NativeToolDefinition {
        return nativeToolDefinition {
            name(toolName)
            description("Based upon the tool requirement, return best relevant tool or tools from the available tools.")
            parameters {
                property(
                    name = "requirement",
                    type = "string",
                    description = "Describe the kind of tools you are looking for. Examples:\n1. I need tools for customer support and help desk operations.\n2. Tools for online shopping and product management.\n3. Tools for data processing and statistical analysis",
                    required = true,
                )
            }
            eventMeta(ToolProgressEventMeta(infix = "agc"))
        }
    }

    override suspend fun executeTool(
        resolvedName: String,
        arguments: String,
        paramsAccessor: ToolParamsAccessor,
        client: OpenAIClient,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
        toolMetadata: Map<String, Any>,
        context: UnifiedToolContext
    ): String {
        val userMessage =
            "## Available Mock Servers with Functions:\n ${getAvailableMockMcpServers()}\n\n## Available Py Function Tools:\n ${getAvailablePythonFunctions()}\n\nTool requirement: $arguments"
        val messages = messages {
            systemMessage(toolSelectorPrompt)
            userMessage(userMessage)
        }

        val selectToolsJson = callModel(paramsAccessor, client, messages)
        val selectedTools: SelectedTools = mapper.readValue(selectToolsJson)
        val mcpMockTools = selectedTools.mcpServers.map {
            platformMcpService.getMockMcpTool(it.serverLabel, it.serverUrl)
        }
        val pyFunTools = selectedTools.pythonFunctions.map {
            FunctionRegistryService.toPyFunTool(funRegService.getFunction(it.name))
        }
        return mapper.writeValueAsString(mapOf("mcpTools" to mcpMockTools, "pyFunTools" to pyFunTools))
    }

    private suspend fun getAvailableMockMcpServers(): String {
        val mockServers = platformMcpService.getAllMockServers()
        return mockServers
            .map { server ->
                val mcpTool =
                    MCPTool(
                        type = "mcp",
                        serverLabel = server.serverLabel,
                        serverUrl = server.url,
                    )
                val tools = toolService.getRemoteMcpTools(mcpTool)
                    .map { it.copy(name = mcpTool.toMCPServerInfo().unQualifiedToolName(it.name ?: "not available")) }

                "Server: ${mapper.writeValueAsString(mcpTool)}\n Tools: ${mapper.writeValueAsString(tools)}"
            }.joinToString("\n-----\n")
    }

    private suspend fun getAvailablePythonFunctions(): String {
        val functions = funRegService.getAllAvailableFunctions(false)
        return functions.joinToString("\n-----\n") {
            mapper.writeValueAsString(
                PyFunTool(
                    type = "py_fun_tool",
                    functionDetails = FunctionDetails(
                        name = it.name,
                        description = it.description,
                        parameters = it.inputSchema?.toMutableMap() ?: mutableMapOf()
                    ),
                    code = "",
                ),
            )
        }
    }

    private val toolSelectorPrompt = """
# Intelligent Tool Selection Assistant

You are an expert tool selection specialist for the AgC (Agentic Compute) platform. Your role is to analyze user requirements and intelligently select the most relevant MCP mock servers and Python functions from the available tool inventory.

## Your Mission:
Match user requirements with the most appropriate tools by understanding the context, functionality needs, and tool capabilities.

## Your Capabilities:
1. **Requirement Analysis** - Parse and understand what tools are needed for a specific use case
2. **Tool Matching** - Map requirements to available MCP mock servers and Python functions
3. **Relevance Scoring** - Evaluate how well each tool fits the user's needs
4. **Intelligent Selection** - Choose only the most relevant tools, avoiding unnecessary ones

## Input Parameters:
- **requirement**: A clear description of what kind of tools are needed (e.g., "I need tools for customer support", "Tools for data analysis", "E-commerce functionality")

## Output Format:
Return a JSON object with the following structure:
```json
{
  "mcpServers": [
    {
      "type": "mcp",
      "server_label": "string: sever-label-value",
      "server_url": "string: sever-url-value"
    }
  ],
  "pythonFunctions": [
    {
      "name": "string: name of function"
    }
  ]
}
```

## Selection Criteria:

### MCP Mock Servers:
- **Function Relevance**: Only include servers with functions that directly support the user's requirements
- **Capability Match**: Evaluate if the server's functions align with the intended use case
- **Avoid Over-selection**: Don't include servers just because they exist - only if they add value

### Python Functions:
- **Functional Fit**: Select functions that provide computational capabilities needed for the use case
- **Dependency Consideration**: Consider if the function's dependencies align with the agent's purpose
- **Performance Impact**: Prefer lightweight functions unless heavy computation is explicitly required

## Selection Principles:
1. **Quality over Quantity**: Select fewer, highly relevant tools rather than many mediocre ones
2. **Context Awareness**: Consider the agent's domain and specific use case
3. **Avoid Redundancy**: Don't select multiple tools that provide similar functionality
4. **Future-Proofing**: Consider if the tools will remain relevant as the agent evolves
5. **Performance Optimization**: Prefer tools that won't slow down the agent unnecessarily

## Example Scenarios:

### Customer Support Agent:
**Requirement**: "I need tools for customer support and help desk operations"
**Expected Selection**: 
- MCP servers with customer data, knowledge base, and ticket management functions
- Python functions for sentiment analysis, response generation, or data processing

### E-commerce Agent:
**Requirement**: "Tools for online shopping and product management"
**Expected Selection**:
- MCP servers with product catalog, cart management, and payment functions
- Python functions for recommendation algorithms or inventory calculations

### Data Analysis Agent:
**Requirement**: "Tools for data processing and statistical analysis"
**Expected Selection**:
- Python functions for data manipulation, statistical calculations, and visualization
- MCP servers with data source connectors if available

## Error Handling:
- If no relevant tools are found, return an empty selection with clear reasoning
- If the requirement is ambiguous, ask for clarification before proceeding
- If tool dependencies conflict, prioritize the most critical requirements

## Response Guidelines:
1. **Be Selective**: Only recommend tools that truly add value
2. **Explain Reasoning**: Provide clear justification for each selection
3. **Consider Alternatives**: Mention if there are better tool options available
4. **Performance Notes**: Highlight any tools that might impact agent performance
5. **Integration Tips**: Suggest how the selected tools can work together

Remember: Your goal is to be a discerning tool curator who selects only the most valuable and relevant tools for each specific use case, ensuring the agent has exactly what it needs without unnecessary complexity.
    """
}

data class SelectedTools(val mcpServers: List<MCPTool>, val pythonFunctions: List<FunctionDetails>)
