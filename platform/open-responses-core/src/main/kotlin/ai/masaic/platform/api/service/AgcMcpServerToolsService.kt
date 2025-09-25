package ai.masaic.platform.api.service

import ai.masaic.platform.api.model.PlatformAgent
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.modelcontextprotocol.server.McpStatelessServerFeatures
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.*
import kotlinx.coroutines.reactor.mono
import mu.KotlinLogging

class AgcMcpServerToolsService(
    private val askAgentService: AskAgentService,
    private val agentService: AgentService,
) {
    private val mapper = jacksonObjectMapper()
    private val log = KotlinLogging.logger { }

    companion object {
        private const val LIST_AGENTS_TOOL_NAME = "list_agents"
        private const val ASK_AGENT_TOOL_NAME = "ask_agent"
    }

    suspend fun provideTools(): List<McpStatelessServerFeatures.AsyncToolSpecification> = listOf(listAgentsTool(), askAgentTool())

    private suspend fun askAgentTool(): McpStatelessServerFeatures.AsyncToolSpecification {
        val askAgentToolDescription =
            """
            This tool can be used to ask a query to an agent. If you are not sure about the available agents, first retrieve list of available agents from $LIST_AGENTS_TOOL_NAME tool.    
            If the incorrect name of the agent is provided then the request will be rejected.
            """.trimIndent()
        val tool =
            McpSchema.Tool
                .builder()
                .name(ASK_AGENT_TOOL_NAME)
                .description(askAgentToolDescription)
                .inputSchema(askAgentInputSchema)
                .build()

        return McpStatelessServerFeatures.AsyncToolSpecification(tool) { exchange, request ->
            log.debug { "exchange=$exchange, request=$request" }
            mono {
                if (request.name != ASK_AGENT_TOOL_NAME) {
                    CallToolResult("incorrect tool name. I can serve $ASK_AGENT_TOOL_NAME tool", true)
                }

                runAskAgentTool(request)
            }
        }
    }

    private suspend fun runAskAgentTool(request: CallToolRequest): CallToolResult {
        val askAgentRequest: AskAgentRequest = mapper.convertValue(request.arguments)
        val toolsResult =
            try {
                log.info { "Running mcp tool for request: $askAgentRequest" }
                val response = askAgentService.askAgent(askAgentRequest)
                log.info { "Agent Tool ${askAgentRequest.agentName} executed successfully." }
                CallToolResult(mapper.writeValueAsString(response), false)
            } catch (ex: Exception) {
                val errorMessage = "Agent executed with exception, errorMessage: ${ex.message}"
                log.error { errorMessage }
                CallToolResult(errorMessage, true)
            }
        log.debug { "MCP tool result: $toolsResult" }
        return toolsResult
    }

    private suspend fun listAgentsTool(): McpStatelessServerFeatures.AsyncToolSpecification {
        val listToolDescription =
            """
            This tool lists all available agents within Agentic Compute Platform (AgC).\n
            The tool returns list that has each agent's name and its description. This tool can be used to identify the best agent available to solve an ongoing query from the user.\n
            - Agent's name - the unique name with which agent is identified within AgC platform.\n
            - Agent's description - this described the nature of tasks this agent can work upon.\n
            This tool is the only source of truth to know available agents within AgC. Example list:\n
              [
                {
                    "name": "B2b_sales_autopilot",
                    "description": "End-to-end B2B sales agent that extracts insights from call transcripts, ensures compliance, calculates optimal discounts, generates proposals, and delivers them to clients via email"
                }
              ]
            """.trimIndent()
        val tool =
            McpSchema.Tool
                .builder()
                .name(LIST_AGENTS_TOOL_NAME)
                .description(listToolDescription)
                .inputSchema(listAgentsInputSchema)
                .build()
        return McpStatelessServerFeatures.AsyncToolSpecification(tool) { exchange, request ->
            log.debug { "exchange=$exchange, request=$request" }
            mono {
                if (request.name != LIST_AGENTS_TOOL_NAME) {
                    CallToolResult("incorrect tool name. I can serve $LIST_AGENTS_TOOL_NAME tool", true)
                }

                runListAgents()
            }
        }
    }

    private suspend fun runListAgents(): CallToolResult =
        try {
            log.info { "Executing list agents tool" }
            val agents = agentService.getAllAgents()
            val agentList =
                agents.map { agent ->
                    TextContent(mapOf("name" to PlatformAgent.presentableName(agent.name), "description" to agent.description).toString())
                }
            log.debug { "agents: $agentList" }
            CallToolResult(agentList, false)
        } catch (ex: Exception) {
            val errorMessage = "List tool executed with exception, errorMessage: ${ex.message}"
            log.error { errorMessage }
            CallToolResult(errorMessage, true)
        }

    private val askAgentInputSchema =
        JsonSchema(
            "object",
            mapOf(
                "agent_name" to
                    mapOf(
                        "type" to "string",
                        "description" to "Name of the agent to whom this query is addressed. Exact name of the agent is available in \$LIST_AGENTS_TOOL_NAME tool.",
                    ),
                "query" to
                    mapOf(
                        "type" to "string",
                        "description" to "Question, for which you are looking answer from Agent. Provide as much possible elaborative quert.",
                    ),
                "context" to
                    mapOf(
                        "type" to "string",
                        "description" to "If there is any relevant context from past conversation is available, provide that as well. Better the context better I will perform in meeting expectations.",
                    ),
                "previous_response_id" to
                    mapOf(
                        "type" to "string",
                        "description" to "Use this parameter when the current query depends on the previous query and its answer. Each agent response includes a \"response_id\" (e.g., {\"content\": \"<answer>\", \"response_id\": \"<id>\"}). That \"response_id\" can be passed here as \"previous_response_id\" in the next request. If the current query is independent, leave this parameter empty.",
                    ),
            ),
            listOf(
                "agent_name",
                "query",
                "context",
            ),
            false,
            emptyMap(),
            emptyMap(),
        )

    private val listAgentsInputSchema =
        JsonSchema(
            "object",
            emptyMap(),
            emptyList(),
            false,
            emptyMap(),
            emptyMap(),
        )
}
