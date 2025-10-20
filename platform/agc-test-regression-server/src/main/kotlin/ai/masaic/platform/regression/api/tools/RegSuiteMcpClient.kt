package ai.masaic.platform.regression.api.tools

import ai.masaic.openresponses.api.service.ResponseNotFoundException
import ai.masaic.platform.regression.api.service.RegSuiteResponseStoreFacade
import ai.masaic.openresponses.tool.NativeToolDefinition
import ai.masaic.openresponses.tool.ToolDefinition
import ai.masaic.openresponses.tool.ToolHosting
import ai.masaic.openresponses.tool.ToolParamsAccessor
import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.openresponses.tool.mcp.McpClient
import ai.masaic.openresponses.tool.mcp.McpToolDefinition
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.utils.JsonSchemaMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.client.OpenAIClient
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent

class RegSuiteMcpClient(
    private val responseStoreFacade: RegSuiteResponseStoreFacade,
) : McpClient {
    private val log = KotlinLogging.logger { }
    private val mapper = jacksonObjectMapper()

    companion object {
        const val REGRESS_SERVER_HOST = "rs.masaic.ai"
        const val REGRESS_SERVER_URL = "https://$REGRESS_SERVER_HOST"
        const val RUN_PW_SCRIPT_TOOL_NAME = "run_play_wright_script"
        const val GET_TEST_TRAIL_TOOL_NAME = "get_test_steps_trail"
    }

    override suspend fun listTools(mcpServerInfo: MCPServerInfo): List<McpToolDefinition> = listOf(toMcpToolDef(runPWToolDef, mcpServerInfo), toMcpToolDef(getTestTrail, mcpServerInfo))

    override suspend fun executeTool(
        tool: ToolDefinition,
        arguments: String,
        paramsAccessor: ToolParamsAccessor?,
        openAIClient: OpenAIClient?,
        headers: Map<String, String>,
        eventEmitter: ((ServerSentEvent<String>) -> Unit)?,
    ): String {
        val argTree = mapper.readTree(arguments)
        return when (tool.name) {
            RUN_PW_SCRIPT_TOOL_NAME -> executePWTool(argTree, tool)
            GET_TEST_TRAIL_TOOL_NAME -> executeGTTool(argTree, tool)
            else -> "Tool: ${tool.name} is unsupported. Can't execute it."
        }
    }

    override suspend fun close() {
        log.info { "Nothing to close in ${RegSuiteMcpClient::class.simpleName}" }
    }

    private suspend fun executePWTool(
        argTree: JsonNode,
        tool: ToolDefinition,
    ): String {
        val toolResult =
            if (!argTree.has("scriptName")) {
                "scriptName is mandatory input parameter of tool. Can't run tool without scriptName parameter."
            } else {
                val scriptName = argTree["scriptName"].asText()
                log.info { "Firing run for script: $scriptName" }
                val runnerResult = PlayWrightRunner().runPlaywrightScript("tests/$scriptName")
                if (runnerResult.success) {
                    "Script executed successfully, output: ${runnerResult.stdout}"
                } else {
                    "Script executed with error, exit_code:${runnerResult.exitCode}, output: ${runnerResult.stdout}, stderr: ${runnerResult.stderr}"
                }
            }
        log.info { "ToolResult: $toolResult" }
        return toolResult
    }

    private suspend fun executeGTTool(
        argTree: JsonNode,
        tool: ToolDefinition,
    ): String {
        var toolResult: String
        toolResult =
            if (!argTree.has("responseId")) {
                "responseId is mandatory input parameter of tool. Can't run tool without responseId parameter."
            } else {
                val responseId = argTree["responseId"].asText()
                log.info { "received respondId:$responseId" }
                val inputItems =
                    try {
                        responseStoreFacade.listInputItems(responseId = responseId, limit = 40, order = "desc", after = null, before = null)
                    } catch (ex: ResponseNotFoundException) {
                        toolResult = "No conversational trail is available against responseId=$responseId"
                        null
                    }

                // Result type allows nullable values inside maps
                val inputMessages: List<Map<String, Any?>> =
                    inputItems?.data?.mapNotNull { item ->
                        when (item.type) {
                            "message" -> {
                                val contentList: List<String> =
                                    (item.content as? List<*>)
                                        ?.mapNotNull { it as? Map<*, *> }
                                        ?.mapNotNull { c ->
                                            if (c["type"] == "input_text") c["text"]?.toString() else null
                                        }.orEmpty()

                                // role is String? -> only emit if non-null
                                item.role?.let { role ->
                                    if (contentList.isNotEmpty()) mapOf(role to contentList) else null
                                }
                            }

                            "function_call" -> {
                                mapOf(
                                    "assistant" to
                                        mapOf(
                                            "type" to "tool_call",
                                            "name" to item.name, // can be String?
                                            "arguments" to item.arguments,
                                            "call_id" to item.call_id, // can be String?
                                        ),
                                )
                            }

                            "function_call_output" -> {
                                mapOf(
                                    "tool" to
                                        mapOf(
                                            "output" to item.output,
                                            "call_id" to item.call_id,
                                        ),
                                )
                            }

                            else -> null
                        }
                    } ?: emptyList()

                val response =
                    try {
                        responseStoreFacade.getResponse(responseId = responseId)
                    } catch (ex: ResponseNotFoundException) {
                        toolResult = "No conversational trail is available against responseId=$responseId"
                        null
                    }

                val outputMessages: List<Map<String, Any?>> =
                    response
                        ?.output()
                        ?.mapNotNull { outputItem ->
                            if (outputItem.message().isPresent) {
                                val msg = outputItem.asMessage()

                                // collect only outputText contents
                                val texts: List<String> =
                                    msg
                                        .content()
                                        .filter { it.outputText().isPresent }
                                        .map { it.outputText().get().text() }

                                // role can be null, so guard it
                                msg._role()?.let { role ->
                                    if (texts.isNotEmpty()) {
                                        mapOf(role.toString() to texts)
                                    } else {
                                        null
                                    }
                                }
                            } else {
                                mapOf("error" to "unknown output item type. Only message outputs are supported")
                            }
                        }
                        ?: emptyList()
                "Test conversation trail:\n" + mapper.writeValueAsString(inputMessages.reversed() + outputMessages)
            }
        log.info { "ToolResult: $toolResult" }
        return toolResult
    }

    private val runPWToolDef =
        nativeToolDefinition {
            name(RUN_PW_SCRIPT_TOOL_NAME)
            description("Run the playwright script to execute test suite")
            parameters {
                property(
                    name = "scriptName",
                    type = "string",
                    description = "name of the playwright script to run",
                    required = true,
                )
                additionalProperties = false
            }
        }

    private val getTestTrail =
        nativeToolDefinition {
            name(GET_TEST_TRAIL_TOOL_NAME)
            description("Returns conversational trail of messages populated during testing run")
            parameters {
                property(
                    name = "responseId",
                    type = "string",
                    description = "Response id received after test run. This responseId will be used to pull out the complete conversational trail.",
                    required = true,
                )
                additionalProperties = false
            }
        }

    private fun toMcpToolDef(
        nativeTool: NativeToolDefinition,
        mcpServerInfo: MCPServerInfo,
    ): McpToolDefinition {
        val parameters = JsonSchemaMapper.mapToJsonSchemaElement(nativeTool.parameters) as JsonObjectSchema
        return McpToolDefinition(
            id = nativeTool.id,
            hosting = ToolHosting.REMOTE,
            name = mcpServerInfo.qualifiedToolName(nativeTool.name),
            description = nativeTool.description,
            parameters = parameters,
            serverInfo = mcpServerInfo,
            eventMeta = nativeTool.eventMeta,
        )
    }
}
