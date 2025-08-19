package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.exception.AgentNotFoundException
import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.platform.api.controller.*
import ai.masaic.platform.api.registry.functions.*
import ai.masaic.platform.api.repository.AgentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AgentService(
    private val agentRepository: AgentRepository,
    private val funRegService: FunctionRegistryService,
) {
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

    private fun getBuiltInAgent(agentName: String): PlatformAgent? =
        when (agentName) {
            "Masaic-Mocky" -> {
                PlatformAgent(
                    name = "Masaic-Mocky",
                    description = "Mocky: Expert in making mock MCP servers quickly",
                    greetingMessage = "Hi, this is Mocky. Let me know the quick mock functions you would like to create.",
                    systemPrompt = mockyPrompt,
                    tools = mockyTools,
                    kind = AgentClass(AgentClass.SYSTEM),
                )
            }
            "ModelTestAgent" -> {
                PlatformAgent(
                    name = "ModelTestAgent",
                    description = "This agent tests compatibility of model with platform",
                    greetingMessage = "Hi, let me test Model with query: \"Tell me the weather of San Francisco\"",
                    systemPrompt = modelTestPrompt,
                    userMessage = "Tell me the weather of San Francisco",
                    tools = modelTestTools,
                    kind = AgentClass(AgentClass.SYSTEM),
                )
            }
            "AgentBuilder" -> {
                PlatformAgent(
                    name = "AgC0",
                    description = "This agent can build agents using available model, tools and system instructions",
                    greetingMessage = "Hi, this is AgC0 agent, I can help you in building agent that can run on my Agentic Compute (AgC)",
                    systemPrompt = agC0Prompt,
                    kind = AgentClass(AgentClass.SYSTEM),
                )
            }
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

    private val agC0Prompt =
        """
        
        """.trimIndent()

    // Import the constants
    private object PlatformToolsNames {
        const val FUN_DEF_GEN_TOOL = "fun_def_generation_tool"
        const val FUN_REQ_GATH_TOOL = "fun_req_gathering_tool"
        const val MOCK_FUN_SAVE_TOOL = "mock_fun_save_tool"
        const val MOCK_GEN_TOOL = "mock_generation_tool"
        const val MOCK_SAVE_TOOL = "mock_save_tool"
        const val MODEL_TEST_TOOL = "get_weather_by_city"
    }

    private object AgentClass {
        const val SYSTEM = "system"
        const val OTHER = "other"
    }
}
