package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.exception.AgentNotFoundException
import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.tool.*
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.controller.*
import ai.masaic.platform.api.registry.functions.*
import ai.masaic.platform.api.repository.AgentRepository
import ai.masaic.platform.api.tools.PlatformMcpService
import ai.masaic.platform.api.tools.PlatformNativeTool
import ai.masaic.platform.api.tools.PlatformToolsNames
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import org.slf4j.LoggerFactory
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service

@Service
class AgentService(
    private val agentRepository: AgentRepository,
    private val funRegService: FunctionRegistryService,
    private val platformMcpService: PlatformMcpService,
    private val toolService: ToolService,
) : PlatformNativeTool(PlatformToolsNames.SAVE_AGENT_TOOL) {
    private val log = LoggerFactory.getLogger(AgentService::class.java)

    suspend fun saveAgent(
        agent: PlatformAgent,
        isUpdate: Boolean,
    ) {
        val existingAgent = agentRepository.findByName(agent.name)
        // For updates, verify the agent exists
        if (isUpdate && existingAgent == null) {
            throw AgentNotFoundException("Agent '${agent.name}' not found.")
        }

        if (!isUpdate && existingAgent != null) {
            throw ResponseProcessingException("Agent '${agent.name}' already exists.")
        }

        // Transform agent.tools to ToolMeta
        val toolMeta = transformToolsToToolMeta(agent.tools)
        
        // Create PlatformAgentMeta for database storage
        val agentMeta =
            PlatformAgentMeta(
                name = agent.name,
                description = agent.description,
                greetingMessage = agent.greetingMessage,
                systemPrompt = agent.systemPrompt,
                userMessage = agent.userMessage,
                toolMeta = toolMeta,
                formatType = agent.formatType,
                temperature = agent.temperature,
                maxTokenOutput = agent.maxTokenOutput,
                topP = agent.topP,
                store = agent.store,
                stream = agent.stream,
                modelInfo = agent.model?.let { ModelInfoMeta(it) },
                kind = agent.kind,
            )

        // Save the agent meta to database (upsert will handle create/update automatically)
        agentRepository.upsert(agentMeta)
    }

    suspend fun getAgent(agentName: String): PlatformAgent? {
        // First check built-in agents
        val builtInAgent = getBuiltInAgent(agentName)
        if (builtInAgent != null) {
            return builtInAgent
        }

        // Then check persisted agents
        val persistedAgentMeta = agentRepository.findByName(agentName) ?: return null
        
        // Convert meta to PlatformAgent
        return convertToPlatformAgent(persistedAgentMeta)
    }

    suspend fun getAllAgents(): List<PlatformAgent> {
        // Only return persisted agents (not SYSTEM agents)
        val persistedAgentMetas = agentRepository.findAll()
        return persistedAgentMetas.map {
            val agent = convertToPlatformAgent(it)
            PlatformAgent(model = agent.model, name = agent.name, description = agent.description, systemPrompt = agent.systemPrompt)
        }
    }

    suspend fun deleteAgent(agentName: String): Boolean {
        // Built-in agents cannot be deleted
        if (getBuiltInAgent(agentName) != null) {
            return false
        }
        
        // Delete persisted agent
        return agentRepository.deleteByName(agentName)
    }

    private suspend fun convertToPlatformAgent(
        agentMeta: PlatformAgentMeta,
        tools: List<Tool>? = null,
    ): PlatformAgent {
        // Convert toolMeta to actual Tool objects if not provided
        val resolvedTools = tools ?: convertToolMetaToTools(agentMeta.toolMeta)
        
        return PlatformAgent(
            model = agentMeta.modelInfo?.name,
            name = agentMeta.name,
            description = agentMeta.description,
            greetingMessage = agentMeta.greetingMessage,
            systemPrompt = agentMeta.systemPrompt,
            userMessage = agentMeta.userMessage,
            tools = resolvedTools,
            formatType = agentMeta.formatType,
            temperature = agentMeta.temperature,
            maxTokenOutput = agentMeta.maxTokenOutput,
            topP = agentMeta.topP,
            store = agentMeta.store,
            stream = agentMeta.stream,
            kind = agentMeta.kind,
        )
    }

    private suspend fun convertToolMetaToTools(toolMeta: ToolMeta): List<Tool> {
        val tools = mutableListOf<Tool>()
        
        toolMeta.let { meta ->
            // Handle MCP tools
            meta.mcpTools.forEach { mcpToolMeta ->
                tools.add(
                    MCPTool(
                        type = "mcp",
                        serverLabel = mcpToolMeta.serverLabel,
                        serverUrl = mcpToolMeta.serverUrl,
                        allowedTools = mcpToolMeta.allowedTools,
                        headers = mcpToolMeta.headers,
                    ),
                )
            }

            // Handle file search tools
            meta.fileSearchTools.forEach { fileSearchToolMeta ->
                tools.add(
                    FileSearchTool(
                        type = "file_search",
                        filters = fileSearchToolMeta.filters,
                        maxNumResults = fileSearchToolMeta.maxNumResults,
                        rankingOptions =
                            fileSearchToolMeta.rankingOptions?.let { rankingOptions ->
                                RankingOptions(
                                    ranker = rankingOptions.ranker,
                                    scoreThreshold = rankingOptions.scoreThreshold,
                                )
                            },
                        vectorStoreIds = fileSearchToolMeta.vectorStoreIds,
                        modelInfo =
                            fileSearchToolMeta.modelInfo.let { modelInfo ->
                                ModelInfo(
                                    bearerToken = null, // Will be resolved at runtime
                                    model = modelInfo.name,
                                )
                            },
                    ),
                )
            }

            // Handle agentic search tools
            meta.agenticSearchTools.forEach { agenticSearchToolMeta ->
                tools.add(
                    AgenticSeachTool(
                        type = "agentic_search",
                        filters = agenticSearchToolMeta.filters,
                        maxNumResults = agenticSearchToolMeta.maxNumResults,
                        vectorStoreIds = agenticSearchToolMeta.vectorStoreIds,
                        maxIterations = agenticSearchToolMeta.maxIterations,
                        enablePresencePenaltyTuning = agenticSearchToolMeta.enablePresencePenaltyTuning,
                        enableFrequencyPenaltyTuning = agenticSearchToolMeta.enableFrequencyPenaltyTuning,
                        enableTemperatureTuning = agenticSearchToolMeta.enableTemperatureTuning,
                        enableTopPTuning = agenticSearchToolMeta.enableTopPTuning,
                        modelInfo = ModelInfo(bearerToken = null, model = agenticSearchToolMeta.modelInfo.name),
                    ),
                )
            }

            meta.pyFunTools.forEach { pyFunTool ->
                val function = funRegService.getFunction(pyFunTool.id)
                tools.add(PyFunTool(type = "py_fun_tool", functionDetails = FunctionDetails(name = function.name, description = function.description, parameters = function.inputSchema), code = function.code, deps = function.deps))
            }
        }
        
        return tools
    }

    private suspend fun transformToolsToToolMeta(tools: List<Tool>): ToolMeta {
        val mcpTools = mutableListOf<McpToolMeta>()
        val fileSearchTools = mutableListOf<FileSearchToolMeta>()
        val agenticSearchTools = mutableListOf<AgenticSearchToolMeta>()
        val pyFunTools = mutableListOf<PyFunToolMeta>()

        tools.forEach { tool ->
            when (tool) {
                is MCPTool -> {
                    mcpTools.add(
                        McpToolMeta(
                            serverLabel = tool.serverLabel,
                            serverUrl = tool.serverUrl,
                            allowedTools = tool.allowedTools,
                            headers = tool.headers,
                        ),
                    )
                }

                is FileSearchTool -> {
                    fileSearchTools.add(
                        FileSearchToolMeta(
                            filters = tool.filters,
                            maxNumResults = tool.maxNumResults,
                            rankingOptions =
                                tool.rankingOptions?.let { rankingOptions ->
                                    RankingOptionsMeta(
                                        ranker = rankingOptions.ranker,
                                        scoreThreshold = rankingOptions.scoreThreshold,
                                    )
                                },
                            vectorStoreIds = tool.vectorStoreIds ?: emptyList(),
                            modelInfo =
                                tool.modelInfo?.let { modelInfo ->
                                    ModelInfoMeta(modelInfo.model ?: throw ResponseProcessingException("Model name is required for FileSearchTool"))
                                } ?: throw ResponseProcessingException("ModelInfo is required for FileSearchTool"),
                        ),
                    )
                }

                is AgenticSeachTool -> {
                    agenticSearchTools.add(
                        AgenticSearchToolMeta(
                            filters = tool.filters,
                            maxNumResults = tool.maxNumResults,
                            vectorStoreIds = tool.vectorStoreIds,
                            maxIterations = tool.maxIterations,
                            enablePresencePenaltyTuning = tool.enablePresencePenaltyTuning,
                            enableFrequencyPenaltyTuning = tool.enableFrequencyPenaltyTuning,
                            enableTemperatureTuning = tool.enableTemperatureTuning,
                            enableTopPTuning = tool.enableTopPTuning,
                            modelInfo =
                                tool.modelInfo?.let { modelInfo ->
                                    ModelInfoMeta(modelInfo.model ?: throw ResponseProcessingException("Model name is required for AgenticSearchTool"))
                                } ?: throw ResponseProcessingException("ModelInfo is required for AgenticSearchTool"),
                        ),
                    )
                }

                is PyFunTool -> {
                    val function = createPyFunToolIfNotPresent(tool)
                    pyFunTools.add(PyFunToolMeta(id = function.name))
                }

                else -> {
                    throw ResponseProcessingException("Unknown tool type: ${tool.type}")
                }
            }
        }

        return ToolMeta(
            mcpTools = mcpTools,
            fileSearchTools = fileSearchTools,
            agenticSearchTools = agenticSearchTools,
            pyFunTools = pyFunTools,
        )
    }

    private suspend fun createPyFunToolIfNotPresent(pyFunTool: PyFunTool): FunctionDoc {
        val function =
            FunctionCreate(
                name = pyFunTool.functionDetails.name,
                description = pyFunTool.functionDetails.description,
                deps = pyFunTool.deps,
                code = pyFunTool.code,
                inputSchema = pyFunTool.functionDetails.parameters,
            )
        return try {
            funRegService.createFunction(function)
        } catch (ex: FunctionRegistryException) {
            if (ex.code == ErrorCodes.NAME_CONFLICT) funRegService.getFunction(pyFunTool.functionDetails.name) else throw ex
        }
    }

    private suspend fun getBuiltInAgent(agentName: String): PlatformAgent? =
        when (agentName) {
            "masaic-mocky" -> {
                PlatformAgent(
                    name = "masaic-mocky",
                    description = "Mocky: Expert in making mock MCP servers quickly",
                    greetingMessage = "Hi, this is Mocky. Let me know the quick mock functions you would like to create.",
                    systemPrompt = mockyPrompt,
                    tools = mockyTools,
                    kind = AgentClass(AgentClass.SYSTEM),
                )
            }
            "modeltestagent" -> {
                PlatformAgent(
                    name = "modeltestagent",
                    description = "This agent tests compatibility of model with platform",
                    greetingMessage = "Hi, let me test Model with query: \"Tell me the weather of San Francisco\"",
                    systemPrompt = modelTestPrompt,
                    userMessage = "Tell me the weather of San Francisco",
                    tools = modelTestTools,
                    kind = AgentClass(AgentClass.SYSTEM),
                )
            }
            "agent-builder" -> getAgentBuilder()
            else -> null
        }

    // Move the built-in agent definitions here
    private val mockyPrompt =
        """
        Function Requirement Gathering, Definition Generation and Mock Creation Workflow  
        - Accept the initial user input describing the function they want to define.  
        - Pass the user input and any gathered details to fun_req_gathering_tool; analyze its feedback for missing requirements.  
        - Prompt the user for any missing details as indicated by fun_req_gathering_tool, incorporating each user response back into fun_req_gathering_tool. If the missing information is available in the context then use the same with more clear context call to tool fun_req_gathering_tool
        - Repeat the gathering and prompting cycle until fun_req_gathering_tool indicates that the requirements gathering is complete.  
        - Once complete, pass the full set of collected requirements to fun_def_generation_tool to generate the function definition.  
        - Present  the final generated function definition to the user.
        - Once user approves the function definition then save the same using mock_fun_save_tool. If mock_fun_save_tool fails then retry to recover from error. 
        - Once function is saved, offer user for mock requests and response generation. If user is interested then accept the initial user input describing the type of mocks they want.
        - Pass the user input and any gathered details to mocks_generation_tool; analyze its feedback for missing requirements.  
        - Prompt the user for any missing details as indicated by mocks_generation_tool, incorporating each user response back into mocks_generation_tool.
        - Repeat the gathering and prompting cycle until mocks_generation_tool indicates that the requirements gathering is complete and mocks are generated.
        - Once user approves the proposed mocks then save the same using mocks_save_tool.
        
        Output format: 
        - Intermediate prompts to user should be bullet points.
        - Present the final function definition with one or two sentences brief about function and enclose the function definition returned by fun_def_generation_tool in ```json```.
        - Present the final proposed mocks with one or two sentences brief about mocks and enclose the mocks returned by mocks_generation_tool in ```json```.
        - Return unique identifiers of saved function definition and saved mocks to user for reference.
        
        **Reminder:** 
        - Keep the user's original requirements intact without adding any assumptions. Continue requirement gathering until fun_req_gathering_tool explicitly indicates completion, then generate and return the function definition.
        - If mock_fun_save_tool is executed then success of the tool is mandatory to proceed to next step.
        - If mocks_save_tool is executed then success of  the tool is mandatory for the workflow completion.
        """.trimIndent()

    private val mockyTools =
        listOf(
            MasaicManagedTool(PlatformToolsNames.FUN_DEF_GEN_TOOL),
            MasaicManagedTool(PlatformToolsNames.FUN_REQ_GATH_TOOL),
            MasaicManagedTool(PlatformToolsNames.MOCK_FUN_SAVE_TOOL),
            MasaicManagedTool(PlatformToolsNames.MOCK_GEN_TOOL),
            MasaicManagedTool(PlatformToolsNames.MOCK_SAVE_TOOL),
        )

    private val modelTestPrompt =
        """
        # Weather Information Provider

        * Accept a city name from the user.
        * Call get_weather_by_city with the provided city name.
        * Return the weather information for the requested location.

        Output format:
        Provide only the weather data with no additional commentary.

        Examples:
        Input: What's the weather in Tokyo?
        Output: [Weather data for Tokyo]

        Input: Weather for New York
        Output: [Weather data for New York]

        **Reminder: Keep responses concise and focused only on the weather data.**
        """.trimIndent()

    private val modelTestTools =
        listOf(
            MasaicManagedTool(PlatformToolsNames.MODEL_TEST_TOOL),
        )

    private fun getAgentBuilderPrompt(
        mockMcpServers: String,
        pyFunTools: String,
    ) = """
# Agent Builder Assistant

You are an expert AI agent builder for the AgC (Agentic Compute) platform. Your role is to help users create custom agents from minimal input through intelligent analysis and automatic configuration.

## Your Mission:
Create complete, functional agents from a single user request with minimal back-and-forth interaction.

## Your Capabilities:
1. **Agent Definition Generation** - Derive agent name and description from user requirements
2. **System Prompt Generation** - Use system_prompt_generator tool to create tailored system prompts
3. **Intelligent Tool Selection** - Automatically select applicable tools from available mock servers and py function tools
4. **One-Shot Agent Creation** - Complete agent creation and save without requiring additional user input

## PlatformAgent Definition Schema:
You must ensure all required fields are populated before calling save_agent tool:

```json
{
  "name": "string (required, lowercase_with_underscores, no spaces)",
  "description": "string (required, 1-2 sentences describing agent purpose)",
  "systemPrompt": "string (required, generated via system_prompt_generator tool)",
  "tools": [
    {
      "type": "mcp", //mock mcp server
      "server_label": "string", (containing characters and '-', no white spaces or '_')
      "server_url": "string", (server url)
      "allowed_tools": [ //list of functions under that tool
        "send_email" 
      ]
    },
    {
      "type": "py_fun_tool", //python function tool
      "tool_def": {
        "name": "string", //name of python function 
        "description": "string", //description of python function 
        "parameters": {
          "type": "object",
          "properties": { //Json-schema of input parameters of function
        }
      },
      "code": "string", //base 64 encoded python code of the function.
    }
  ]
}
```

## Required Fields Validation:
Before calling save_agent, ensure you have:
- ✅ **name**: Valid agent name (lowercase_with_underscores)
- ✅ **description**: Clear 1-2 sentence description
- ✅ **systemPrompt**: Generated via system_prompt_generator tool
- ✅ **tools**: Array of selected tools (can be empty if no applicable tools found)

## Available Tool Types:
- **MCP Tools**: Model Context Protocol tools for external integrations
- **Py Function Tools**: Custom Python function execution

## Available Mock Servers with Functions:
$mockMcpServers

## Available Py Function Tools:
$pyFunTools

## Workflow (Single-Shot Execution):
1. **Analyze** user requirements to understand the agent's purpose
2. **Derive** agent name (valid format: lowercase with underscores, no spaces) and description (1-2 sentences)
3. **Generate** system prompt using system_prompt_generator tool with the user requirements
4. **Auto-select** applicable tools by scanning:
   - Available mock MCP servers and their functions
   - Available py function tools
   - Only select tools that directly support the agent's purpose
5. **Validate** all required fields are populated according to PlatformAgent schema
6. **Create** complete agent definition with all required and applicable optional fields
7. **Save** agent using save_agent tool with complete PlatformAgent object
8. **Deliver** ready-to-use agent to user

## Selection Criteria for Tools:
- **Mock MCP Servers**: Select if the agent's use case directly matches available mock functions
- **Py Function Tools**: Select if the agent needs computational capabilities that match available functions
- **Preference Order**: Prioritize mock servers with relevant functions, then py function tools
- **Relevance**: Only include tools that are essential for the agent's core functionality

## Schema Compliance Checklist:
Before saving, verify:
- [ ] Agent name follows naming convention (lowercase_with_underscores)
- [ ] Description is meaningful and concise (1-2 sentences)
- [ ] System prompt has been generated via system_prompt_generator tool
- [ ] Tools array contains only applicable, properly configured tools
- [ ] Complete PlatformAgent object is ready for save_agent tool

## Response Format:
- Be decisive and efficient
- Provide clear reasoning for tool selections
- Present the final agent configuration with schema validation
- Offer immediate usage: "Your agent '[agent_name]' is ready! You can start using it now."
- Include agent summary with selected tools and their purposes

## Key Principles:
- **Schema Compliance**: Always ensure PlatformAgent schema requirements are met
- **Minimal Input Required**: Work with whatever the user provides
- **Intelligent Defaults**: Make smart assumptions based on common use cases
- **Quality over Quantity**: Select fewer, more relevant tools rather than many tools
- **Immediate Utility**: Ensure the agent is immediately functional after creation

## Example Flow:
User: "I want a customer support agent"
→ Analyze: Customer support needs query handling, knowledge access
→ Derive: Name: "customer_support_agent", Description: "AI agent that handles customer inquiries and provides support assistance"
→ Generate: System prompt for customer support using system_prompt_generator
→ Select: Relevant mock functions (if available) for customer data, knowledge base access
→ Validate: Check all required fields (name ✅, description ✅, systemPrompt ✅, tools ✅)
→ Save: Complete PlatformAgent object with all configurations
→ Deliver: "Your agent 'customer_support_agent' is ready! You can start chatting with it now."

## Error Handling:
If any required field is missing or invalid:
1. Identify the missing/invalid field
2. Attempt to derive/generate the missing information
3. If unable to derive, ask user for minimal additional input
4. Retry agent creation once complete

Remember: Your goal is to create a complete, functional agent from the user's initial request without requiring additional configuration steps, while ensuring full compliance with the PlatformAgent schema.
        """.trimIndent()

    private suspend fun getAgentBuilder(): PlatformAgent {
        val mockServers = platformMcpService.getAllMockServers()
        val availableServerDetails =
            mockServers
                .map { server ->
                    val mcpTool =
                        MCPTool(
                            type = "mcp",
                            serverLabel = server.serverLabel,
                            serverUrl = server.url,
                        )
                    val tools = toolService.getRemoteMcpTools(mcpTool).map { it.copy(name = mcpTool.toMCPServerInfo().unQualifiedToolName(it.name ?: "not available")) }

                    "Server: ${mapper.writeValueAsString(mcpTool)}\n Tools: ${mapper.writeValueAsString(tools)}"
                }.joinToString("\n-----\n")

        val functions = funRegService.getAllAvailableFunctions(false)
        val pyFunTools =
            functions.joinToString("\n-----\n") {
                mapper.writeValueAsString(
                    PyFunTool(
                        type = "py_fun_tool",
                        functionDetails = FunctionDetails(name = it.name, description = it.description),
                        code = "",
                    ),
                )
            }
        val prompt = getAgentBuilderPrompt(availableServerDetails, pyFunTools)
        return PlatformAgent(
            name = "AgC0",
            description = "This agent helps in building agents",
            greetingMessage = "Hi, this is AgC0 agent, I can help you in building agent that can run on Agentic Compute (AgC)",
            systemPrompt = prompt,
            kind = AgentClass(AgentClass.SYSTEM),
            tools = listOf(MasaicManagedTool(PlatformToolsNames.SYSTEM_PROMPT_GENERATOR_TOOL), MasaicManagedTool(PlatformToolsNames.SAVE_AGENT_TOOL)),
        )
    }

    override fun provideToolDef(): NativeToolDefinition =
        nativeToolDefinition {
            name("save_agent_tool")
            description("Save or update a platform agent with complete configuration including tools, model settings, and behavior parameters")
            eventMeta(ToolProgressEventMeta("agc"))
            parameters {
                // Define the agent property with complete PlatformAgent schema
                objectProperty(
                    name = "agent",
                    description = "Complete PlatformAgent object with all required fields and optional configurations",
                    required = true,
                ) {
                    nullableProperty("model", "string", "AI model to use for the agent")
                    property("name", "string", "Unique agent name (lowercase_with_underscores)", required = true)
                    property("description", "string", "Brief description of what the agent does", required = true)
                    nullableProperty("greetingMessage", "string", "Welcome message shown to users")
                    property("systemPrompt", "string", "Agent behavior and personality instructions", required = true)
                    nullableProperty("userMessage", "string", "Initial user message to start with")
                    
                    arrayProperty("tools", "Available tools for the agent", default = emptyList<Any>()) {
                        itemsOneOf("MCPTool", "PyFunTool")
                    }
                    
                    property("formatType", "string", "Output format", default = "text")
                    property("temperature", "number", "Response creativity level", default = 1.0)
                    property("max_output_tokens", "integer", "Maximum response length", default = 2048)
                    property("top_p", "number", "Nucleus sampling parameter", default = 1.0)
                    property("store", "boolean", "Whether to persist the agent", default = true)
                    property("stream", "boolean", "Whether to stream responses", default = true)
                    
                    additionalProperties = false
                }
                
                property(
                    name = "isUpdate",
                    type = "boolean",
                    description = "Flag indicating whether this is an update request (true) or create request (false). Defaults to false for create.",
                    required = false,
                )

                // Define MCPTool schema
                definition("MCPTool") {
                    property("type", "string", "Tool type identifier", required = true)
                    property("server_label", "string", "Server label for identification", required = true)
                    property("server_url", "string", "URL of the MCP server", required = true)
                    property("require_approval", "string", "Approval requirement level", default = "never")
                    
                    arrayProperty("allowed_tools", "List of allowed tool functions", default = emptyList<String>()) {
                        items("string")
                    }
                    
                    objectProperty(
                        "headers", 
                        "HTTP headers for server communication", 
                        default = emptyMap<String, String>(),
                        additionalProps = true,
                    ) {
                        // Headers can contain any string values
                    }
                    
                    additionalProperties = false
                }

                // Define PyFunTool schema
                definition("PyFunTool") {
                    property("type", "string", "Tool type identifier", required = true)
                    refProperty("tool_def", "FunctionDetails", "Function definition details", required = true)
                    property("code", "string", "Python function code", required = true)
                    
                    arrayProperty("deps", "Python dependencies", default = emptyList<String>()) {
                        items("string")
                    }
                    
                    nullableProperty("code_interpreter", "object", "Reference to PyInterpreterServer")
                    
                    additionalProperties = false
                }

                // Define FunctionDetails schema
                definition("FunctionDetails") {
                    property("type", "string", "Function type", default = "function", enum = listOf("function"))
                    property("description", "string", "Function description", required = true)
                    property("name", "string", "Function name", required = true)
                    
                    objectProperty(
                        "parameters",
                        "JSON Schema for function parameters", 
                        default = emptyMap<String, Any>(),
                        additionalProps = true,
                    ) {
                        property("additionalProperties", "boolean", enum = listOf(false))
                    }
                    
                    property("strict", "boolean", "Strict parameter validation", default = true)
                    
                    additionalProperties = false
                }

                additionalProperties = false
            }
        }

    override suspend fun executeTool(
        resolvedName: String,
        arguments: String,
        paramsAccessor: ToolParamsAccessor,
        client: OpenAIClient,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
        toolMetadata: Map<String, Any>,
        context: UnifiedToolContext,
    ): String? {
        // Parse the arguments to get both agent and isUpdate flag
        val argsMap: Map<String, Any> = mapper.readValue(arguments)
        val agent: PlatformAgent = mapper.convertValue(argsMap["agent"], PlatformAgent::class.java)
        val isUpdate: Boolean = agentRepository.findByName(agent.name) != null
        saveAgent(agent = agent, isUpdate = isUpdate)
        return "Agent '${agent.name}' ${if (isUpdate) "updated" else "created"} successfully"
    }
}

object AgentClass {
    const val SYSTEM = "system"
    const val OTHER = "other"
}
