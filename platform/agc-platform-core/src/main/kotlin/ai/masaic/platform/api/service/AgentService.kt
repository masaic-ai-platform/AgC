package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.AccessDeniedException
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.api.user.AccessManager
import ai.masaic.openresponses.tool.NativeToolDefinition
import ai.masaic.openresponses.tool.ToolParamsAccessor
import ai.masaic.openresponses.tool.ToolProgressEventMeta
import ai.masaic.openresponses.tool.UnifiedToolContext
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.model.*
import ai.masaic.platform.api.registry.functions.*
import ai.masaic.platform.api.repository.AgentRepository
import ai.masaic.platform.api.tools.PlatformMcpService
import ai.masaic.platform.api.tools.PlatformNativeTool
import ai.masaic.platform.api.tools.PlatformToolsNames
import ai.masaic.platform.api.tools.TemporalConfig
import ai.masaic.platform.api.user.UserInfoProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import org.slf4j.LoggerFactory
import org.springframework.http.codec.ServerSentEvent
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.SecretKeyFactory
import java.security.spec.KeySpec
import kotlin.let

class AgentService(
    private val agentRepository: AgentRepository,
    private val funRegService: FunctionRegistryService,
    private val platformMcpService: PlatformMcpService,
    private val temporalConfig: TemporalConfig?,
) : PlatformNativeTool(PlatformToolsNames.SAVE_AGENT_TOOL) {
    private val log = LoggerFactory.getLogger(AgentService::class.java)

    suspend fun saveAgent(
        agent: PlatformAgent,
        isUpdate: Boolean,
    ) {
        var updatedAgent = agent.copy(name = PlatformAgent.persistenceName(agent.name))
        val existingAgent = agentRepository.findByName(updatedAgent.name)
        // For updates, verify the agent exists
        if (isUpdate && existingAgent == null) {
            throw ResponseProcessingException("Agent '${updatedAgent.name}' not found.")
        }

        if (!isUpdate && existingAgent != null) {
            throw ResponseProcessingException("Agent '${updatedAgent.name}' already exists.")
        }

        // For updates, check access permission
        if (isUpdate && existingAgent != null) {
            if (!AccessManager.isAccessPermitted(existingAgent.accessControlJson).update) {
                throw AccessDeniedException("Access denied to agent: ${updatedAgent.name}")
            }
            updatedAgent = updatedAgent.copy(suggestedQueries = existingAgent.suggestedQueries)
        }

        // Compute access control for new agents, preserve for updates
        val accessControl =
            if (isUpdate) {
                existingAgent?.accessControlJson?.let { AccessManager.fromString(it) }
            } else {
                AccessManager.computeAccessControl()
            }

        // Transform agent.tools to ToolMeta
        val toolMeta = transformToolsToToolMeta(updatedAgent.tools)
        
        // Create PlatformAgentMeta for database storage
        val agentMeta =
            PlatformAgentMeta(
                name = updatedAgent.name,
                description = updatedAgent.description,
                greetingMessage = updatedAgent.greetingMessage,
                systemPrompt = updatedAgent.systemPrompt,
                userMessage = updatedAgent.userMessage,
                suggestedQueries = updatedAgent.suggestedQueries,
                toolMeta = toolMeta,
                formatType = updatedAgent.formatType,
                temperature = updatedAgent.temperature,
                maxTokenOutput = updatedAgent.maxTokenOutput,
                topP = updatedAgent.topP,
                store = updatedAgent.store,
                stream = updatedAgent.stream,
                modelInfo = updatedAgent.model?.let { ModelInfoMeta(it) },
                kind = updatedAgent.kind,
                accessControlJson = accessControl?.let { AccessManager.toString(accessControl) },
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
        val persistedAgentMeta = agentRepository.findByName(PlatformAgent.persistenceName(agentName)) ?: return null
        
        // Check access permission
        if (!AccessManager.isAccessPermitted(persistedAgentMeta.accessControlJson).read) {
            throw AccessDeniedException("Access denied to agent: $agentName")
        }
        
        // Convert meta to PlatformAgent
        return convertToPlatformAgent(persistedAgentMeta)
    }

    suspend fun getAllAgents(): List<PlatformAgent> {
        // Only return persisted agents (not SYSTEM agents)
        val persistedAgentMetas = agentRepository.findAll()
        
        // Filter by access permission
        val accessibleAgentMetas =
            persistedAgentMetas.filter { agentMeta ->
                AccessManager.isAccessPermitted(agentMeta.accessControlJson).read
            }
        
        return accessibleAgentMetas.map {
            val agent = convertToPlatformAgent(it)
            PlatformAgent(model = agent.model, name = agent.name, description = agent.description, systemPrompt = agent.systemPrompt)
        }
    }

    suspend fun deleteAgent(agentName: String): Boolean {
        // Built-in agents cannot be deleted
        val updatedAgentName = PlatformAgent.persistenceName(agentName)
        if (getBuiltInAgent(updatedAgentName) != null) {
            return false
        }
        
        // Check access permission before deletion
        val existingAgent = agentRepository.findByName(updatedAgentName)
        if (existingAgent != null) {
            if (!AccessManager.isAccessPermitted(existingAgent.accessControlJson).delete) {
                throw AccessDeniedException("Access denied to delete agent: $agentName")
            }
        }
        
        // Delete persisted agent
        return agentRepository.deleteByName(updatedAgentName)
    }

    suspend fun getAgentCredentials(agentName: String, toolType: String): String? {
        getAgent(agentName) ?: return null
        if (toolType.lowercase() == "client_side") {
            val userId = UserInfoProvider.userId() ?: throw ResponseProcessingException("User ID not available")
            val credentials = mutableMapOf<String, String>()
            credentials["userId"] = userId
            // Add temporal config if available
            temporalConfig?.let { config ->
                config.target?.let { credentials["target"] = it }
                config.namespace?.let { credentials["namespace"] = it }
                config.apiKey?.let { credentials["apiKey"] = it }
            }
            val jsonString = ObjectMapper().writeValueAsString(credentials)
            val response = mapOf("creds" to encryptCredentials(jsonString))
            return ObjectMapper().writeValueAsString(response)
        } else {
            throw ResponseProcessingException("Unsupported tool type: $toolType. Only 'client_side' is supported.")
        }
    }

    private suspend fun convertToPlatformAgent(
        agentMeta: PlatformAgentMeta,
        tools: List<Tool>? = null,
    ): PlatformAgent {
        // Convert toolMeta to actual Tool objects if not provided
        val resolvedTools = tools ?: convertToolMetaToTools(agentMeta.toolMeta)
        
        return PlatformAgent(
            model = agentMeta.modelInfo?.name,
            name = PlatformAgent.presentableName(agentMeta.name),
            description = agentMeta.description,
            greetingMessage = agentMeta.greetingMessage,
            systemPrompt = agentMeta.systemPrompt,
            userMessage = agentMeta.userMessage,
            suggestedQueries = agentMeta.suggestedQueries,
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

            meta.functionTools.forEach { func ->
                val functionTool =
                    FunctionTool(
                        type = "function",
                        name = func.name,
                        description = func.description,
                        parameters = func.parameters,
                        strict = func.strict,
                    )
                tools.add(functionTool)
            }
        }
        
        return tools
    }

    private suspend fun transformToolsToToolMeta(tools: List<Tool>): ToolMeta {
        val mcpTools = mutableListOf<McpToolMeta>()
        val fileSearchTools = mutableListOf<FileSearchToolMeta>()
        val agenticSearchTools = mutableListOf<AgenticSearchToolMeta>()
        val pyFunTools = mutableListOf<PyFunToolMeta>()
        val functionTools = mutableListOf<FunctionToolMeta>()

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
                                    ModelInfoMeta(modelInfo.model ?: throw ResponseProcessingException("Filesearch tool is missing embedding model selection. Select embeddings model."))
                                } ?: throw ResponseProcessingException("Filesearch tool is missing embedding model selection. Select embeddings model."),
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
                                    ModelInfoMeta(modelInfo.model ?: throw ResponseProcessingException("AgenticSearch tool is missing embedding model selection. Select embeddings model."))
                                } ?: throw ResponseProcessingException("AgenticSearch tool is missing embedding model selection. Select embeddings model."),
                        ),
                    )
                }

                is PyFunTool -> {
                    val function = createPyFunToolIfNotPresent(tool)
                    pyFunTools.add(PyFunToolMeta(id = function.name))
                }

                is FunctionTool -> {
                    val functionToolMeta =
                        FunctionToolMeta(
                            description = tool.description,
                            name = tool.name,
                            parameters = tool.parameters,
                            strict = tool.strict,
                        )
                    functionTools.add(functionToolMeta)
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
            functionTools = functionTools,
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

    private fun getAgentBuilderPrompt() =
        """
# Agent Builder Assistant v2.1

You are an expert AI agent builder for the AgC (Agentic Compute) platform. Your role is to help users create custom agents by making intelligent assumptions while asking for clarification only when truly necessary.

## Your Mission:
Create functional agents efficiently by understanding user intent and making smart defaults, asking for clarification only when the request is genuinely ambiguous.

## Available Tools:
- **${PlatformToolsNames.SYSTEM_PROMPT_GENERATOR_TOOL}**: Creates customized system prompts based on requirements
- **${PlatformToolsNames.TOOL_SELECTOR_TOOL}**: Intelligently selects relevant tools based on agent requirements  
- **${PlatformToolsNames.SAVE_AGENT_TOOL}**: Saves the complete agent configuration

## Smart Agent Creation Workflow:

### 1. Intelligent Analysis & Assumptions
When a user provides a request:

1. **Extract Key Information**:
   - Agent purpose/domain (even if basic)
   - Likely functionality needs
   - Probable tool requirements

2. **Make Smart Defaults**:
   - Generate appropriate agent name from purpose
   - Create meaningful description
   - Assume standard configuration unless specified otherwise

3. **Proceed with Creation** unless the request is genuinely unclear

### 2. When to Ask Questions (Sparingly)
Only ask for clarification when:
- The request is extremely vague (e.g., just "create an agent")
- Multiple valid interpretations exist that would lead to very different agents
- Critical functionality details are missing that can't be reasonably assumed

### 3. Efficient Agent Building Process

**For Clear Requests** (most cases):
1. Derive agent name and description immediately
2. Generate system prompt using ${PlatformToolsNames.SYSTEM_PROMPT_GENERATOR_TOOL}
3. If tools might be useful, use ${PlatformToolsNames.TOOL_SELECTOR_TOOL} with inferred requirements
4. Create and save the agent
5. Provide summary and JSON

**For Ambiguous Requests** (rare cases):
1. Ask 1-2 focused questions maximum
2. Proceed with creation using provided info + smart defaults

## Example Approaches:

### ✅ Good - Make Progress:
User: "I want a customer support agent"
Response: I'll create a customer support agent for you. Let me build this with standard customer service capabilities.
Proceeds to create agent with customer support tools and appropriate system prompt]
✅ Agent Created Successfully!
- Agent Name: customer_support_agent
- Purpose: AI agent that handles customer inquiries and provides support assistance
- Tools Selected: 2 MCP tools (customer data, knowledge base)
- Ready for Use: Yes

### ✅ Good - Smart Questions When Needed:
User: "Create an agent"
Response: "I'd be happy to create an agent for you! What should this agent help with? (e.g., customer support, data analysis, e-commerce, etc.)"

### ❌ Avoid - Too Many Questions:
User: "I want a customer support agent"
Response: "What specific tasks should it handle? What industry? Who will use it? What integrations do you need?" 
*(Too many questions for a clear request)*

## Agent Modification Workflow:

### Detecting Modification Requests
Look for message:
- Modify the agent with name: agentName='{name of the agent}'
- Example: Modify the agent with name: agentName='Ecommerce-Agent' where Ecommerce-Agent is the name of the agent
- This message also contains existing system prompt and tools of the agent. 

### Modification Process
1. **Get Current Agent**: Look for message - Modify the agent with name: agentName='{name of the agent}'
2. **Analyze Changes**: Understand what specific modifications are requested
3. **Apply Updates**: 
   - For tool changes: Use ${PlatformToolsNames.TOOL_SELECTOR_TOOL} with new requirements
   - For system prompt changes: Use ${PlatformToolsNames.SYSTEM_PROMPT_GENERATOR_TOOL} with updated requirements
   - For other changes: Modify the relevant fields directly
4. **Save Updated Agent**: Use ${PlatformToolsNames.SAVE_AGENT_TOOL} tool with isUpdate=true
5. **Show Changes**: Clearly indicate what was modified
    
### Modification Examples:
- **Tool Addition**: "Add e-commerce tools to my customer_support_agent"
- **System Prompt Update**: "Make the support_agent more friendly and casual"
- **Description Change**: "Update the description to mention it handles refunds too"
- **Tool Removal**: "Remove the data analysis tools from my general_assistant"

## Smart Defaults by Domain:
### Customer Support:
- Name: "customer_support_agent"
- Tools: Customer data, knowledge base, ticket management
- Greeting: "Hi! I'm here to help with your questions and concerns."

### E-commerce:
- Name: "ecommerce-assistant"  
- Tools: Product catalog, cart management, order tracking
- Greeting: "Welcome! I can help you find products and manage your orders."

### Data Analysis:
- Name: "data-analyst_agent"
- Tools: Python functions for data processing, statistical analysis
- Greeting: "I'm ready to help you analyze and understand your data."

### General/Assistant:
- Name: "general_assistant"
- Tools: Basic utility functions if available
- Greeting: "Hello! I'm here to assist you with various tasks."

## Tool Selection Strategy:
- **Always call tool_selector** if the agent domain suggests tools might be useful
- Use inferred requirements: "Tools for [domain] functionality like [likely needs]"
- Don't worry about perfect tool selection - users can modify later

## Agent Creation Process:
1. **Quick Analysis**: Understand the core request (15 seconds of thinking)
2. **Generate Components**:
   - Name: domain_based_name
   - Description: Clear purpose statement
   - System Prompt: Use ${PlatformToolsNames.SYSTEM_PROMPT_GENERATOR_TOOL} with inferred requirements
   - Tools: Use ${PlatformToolsNames.TOOL_SELECTOR_TOOL} with domain-based requirements
3. **Create & Save**: Complete agent with all components
4. **Deliver Results**: Summary + JSON

## Agent Creation/Modification Process:
1. **Determine Operation**: Check if this is creating new agent or modifying existing
2. **For Modifications**: 
   - Use Modify the agent with name: agentName='{name of the agent}'
   - Apply requested changes to existing configuration
   - Use save_agent with isUpdate=true
3. **For New Agents**:
   - [Keep existing creation process]

## Response Format:
[Brief acknowledgment and action taken]
[Working indicators during creation]
### For New Agents:
✅ Agent Created Successfully!
Agent Summary:
- Name: agent_name
- Purpose: One-line description
- Tools: X tools selected (brief list)
- Ready: Yes, you can start using it now

### For Agent Modifications:
✅ Agent Updated Successfully!
Changes Made:
- [Field]: [Old value] → [New value]
- Tools: Added [X], Removed [Y]
- System Prompt: Updated for [reason]
- Updated Agent Summary:
- Name: agent_name
- Purpose: Updated description
- Tools: X tools (Y added, Z removed)

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

## Key Principles:
1. **Progress Over Perfection**: Create working agents quickly, users can refine later
2. **Smart Assumptions**: Use domain knowledge to fill gaps intelligently  
3. **Minimal Questions**: Only ask when truly necessary for basic functionality
4. **Default to Action**: When in doubt, create something useful rather than asking more questions
5. **Clear Communication**: Show what you're doing and why
6. **Seamless Modifications**: Handle agent updates as smoothly as new creation
7. **Change Transparency**: Clearly show what was modified and why
8. **Preserve Existing**: Only change what's requested, keep everything else intact
9. **Agent Name Preservation**: During any modification stick with the original name of the agent provided with Modify agent message against variable 'agentName'.

Remember: Your goal is to be helpful and productive. Most users want to see progress and results, not extensive Q&A sessions. Create functional agents quickly and let users iterate from there.
        """.trimIndent()

    private suspend fun getAgentBuilder(): PlatformAgent {
        val prompt = getAgentBuilderPrompt()
        return PlatformAgent(
            name = "AgC0",
            description = "This agent helps in building agents",
            greetingMessage = "Hey Arun, I’m your AgC. Tell me what you want to build.",
            systemPrompt = prompt,
            kind = AgentClass(AgentClass.SYSTEM),
            tools = listOf(MasaicManagedTool(PlatformToolsNames.SYSTEM_PROMPT_GENERATOR_TOOL), MasaicManagedTool(PlatformToolsNames.TOOL_SELECTOR_TOOL), MasaicManagedTool(PlatformToolsNames.SAVE_AGENT_TOOL)),
        )
    }

    override fun provideToolDef(): NativeToolDefinition =
        nativeToolDefinition {
            name(PlatformToolsNames.SAVE_AGENT_TOOL)
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

                property(
                    name = "agentName",
                    type = "string",
                    description = "Original agentName passed in the Modify Agent message - Modify the agent with name: agentName='{name of the agent}'",
                    required = true,
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
    ): String {
        try {
            val request: SaveAgentRequest = mapper.readValue(arguments)
            if (request.agentName != request.agent.name) {
                val errorSaveResponse = SaveAgentResponse(agentName = request.agentName, message = "User requested modification for agent=${request.agentName} but agent object contains agentName=${request.agent.name}. Can't perform save, correct agent.name in agent object.")
                log.info(errorSaveResponse.message)
                return mapper.writeValueAsString(errorSaveResponse)
            }
            val isUpdate: Boolean = agentRepository.findByName(PlatformAgent.persistenceName(request.agent.name)) != null

            val toolsToSave: List<Tool> =
                try {
                    prepareToolsToSave(request.agent.tools)
                } catch (ex: AgentBuilderException) {
                    return ex.message ?: "AgentBuilderException occurred. Can't save agent"
                }

            saveAgent(agent = request.agent.copy(tools = toolsToSave), isUpdate = isUpdate)
            val saveResponse = SaveAgentResponse(agentName = request.agentName, isSuccess = true, message = "Agent '${request.agent.name}' ${if (isUpdate) "updated" else "created"} successfully")
            log.info(saveResponse.message)
            return mapper.writeValueAsString(saveResponse)
        } catch (ex: Exception) {
            val errorMessage = "Save agent failed with error: ${ex.message}"
            log.error(errorMessage, ex)
            return mapper.writeValueAsString(SaveAgentResponse(agentName = "not available", isSuccess = false, message = errorMessage))
        }
    }

    private suspend fun prepareToolsToSave(tools: List<Tool>) =
        tools.map { tool ->
            when (tool) {
                is PyFunTool -> {
                    val function = funRegService.getFunction(tool.functionDetails.name)
                    FunctionRegistryService.toPyFunTool(function)
                }
                is MCPTool -> {
                    if (tool.serverUrl.endsWith(PlatformMcpService.MOCK_UURL_ENDS_WITH)) {
                        platformMcpService.getMockMcpTool(serverLabel = tool.serverLabel, url = tool.serverUrl)
                    } else {
                        throw AgentBuilderException("tool provided with label=${tool.serverLabel} and url=${tool.serverUrl} is not known. Correct the provided tool and then send save agent request.")
                    }
                }
                else -> throw AgentBuilderException("unknown tool type. At the moment I can save agents with tools like McpTool and PyFunTool/")
            }
        }

    private fun encryptCredentials(jsonString: String): String {
        return try {
            // Convert JSON string to bytes
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            // Create salt (8 bytes)
            val salt = ByteArray(8)
            java.security.SecureRandom().nextBytes(salt)

            // Create key and IV using PBKDF2 with empty password (as in your command)
            // Generate 384 bits (48 bytes) = 32 bytes for key + 16 bytes for IV
            val keySpec: KeySpec = PBEKeySpec("".toCharArray(), salt, 100000, 384)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyIv = keyFactory.generateSecret(keySpec).encoded
            
            // Split key and IV (32 bytes key + 16 bytes IV)
            val key = ByteArray(32)
            val iv = ByteArray(16)
            System.arraycopy(keyIv, 0, key, 0, 32)
            System.arraycopy(keyIv, 32, iv, 0, 16)
            
            val secretKey = SecretKeySpec(key, "AES")

            // Encrypt using AES-256-CBC with derived IV
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val encryptedBytes = cipher.doFinal(jsonBytes)

            // Combine "Salted__" + salt + encrypted data (OpenSSL format)
            val combined = ByteArrayOutputStream().use { baos ->
                baos.write("Salted__".toByteArray(Charsets.US_ASCII)) // OpenSSL header
                baos.write(salt)
                baos.write(encryptedBytes)
                baos.toByteArray()
            }
            // Encode to base64
            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            log.error("Failed to encrypt credentials", e)
            throw ResponseProcessingException("Failed to encrypt credentials: ${e.message}")
        }
    }
}

class AgentBuilderException(
    message: String,
) : ResponseProcessingException(message)

data class SaveAgentRequest(
    val agentName: String,
    val isUpdate: Boolean,
    val agent: PlatformAgent,
)

data class SaveAgentResponse(
    val agentName: String,
    val isSuccess: Boolean = false,
    val message: String,
)
