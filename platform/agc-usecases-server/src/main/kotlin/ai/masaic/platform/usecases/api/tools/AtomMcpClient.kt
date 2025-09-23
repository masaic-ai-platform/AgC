package ai.masaic.platform.usecases.api.tools

import ai.masaic.openresponses.tool.*
import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.openresponses.tool.mcp.McpClient
import ai.masaic.openresponses.tool.mcp.McpToolDefinition
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.utils.JsonSchemaMapper
import ai.masaic.platform.usecases.api.service.AtomTemporalWorkflowService
import ai.masaic.platform.usecases.api.service.AtomWorkflowInput
import ai.masaic.platform.usecases.api.service.AtomWorkflowService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.client.OpenAIClient
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import java.util.*

class AtomMcpClient(
    private val atomWorkflowService: AtomWorkflowService,
    private val temporalService: AtomTemporalWorkflowService,
) : McpClient {
    private val log = KotlinLogging.logger { }
    private val mapper = jacksonObjectMapper()

    companion object {
        const val ATOM_CALL_LOG_TOOL_NAME = "call_log_update"
        const val ATOM_PAST_CALLS_LOGS_TOOL_NAME = "get_past_calls_logs"
        private const val EVENT_PREFIX = "response.mcp_call.Sales-Force"

        val callLogUpdateToolDef =
            nativeToolDefinition {
                name(ATOM_CALL_LOG_TOOL_NAME)
                description("This tool can update call logs in sales force. Call logs are generally updated after sales meeting with the customer.")
                parameters {
                    property(
                        name = "workflow_id",
                        type = "enum",
                        description = "Id of the workflow to be executed",
                        enum = listOf("ff8735d6-4bc0-4c3b-9dee-030776440c72"),
                        required = true,
                    )
                    objectProperty(
                        name = "Input",
                        description = "The object that contains information about account to search, call log, opportunity.",
                        required = true,
                    ) {
                        property(
                            name = "search_term",
                            type = "string",
                            description = "The account name for search. For example - BW, Siemens, Volkswagen",
                            required = true,
                        )
                        property(
                            name = "subject_option",
                            type = "enum",
                            description = "The subject, always picked from enum",
                            enum = listOf("Call"),
                            required = true,
                        )
                        property(
                            name = "call_details",
                            type = "string",
                            description = "The discussion of the call to be logged. Can be a bullet list that summarised the call in various sections like: progress, client mood, pain-points, opportunities",
                        )
                        property(
                            name = "contact_name",
                            type = "string",
                            description = "Name/designation of the contact person.",
                            required = true,
                        )
                        property(
                            name = "opportunity_name",
                            type = "string",
                            description = "concise one sentence about the opportunity available.",
                            required = true,
                        )
                        property(
                            name = "opportunity_description",
                            type = "string",
                            description = "Detailed description of the opportunity",
                        )
                        property(
                            name = "close_date",
                            type = "string",
                            description = "Expected close date of the opportunity. If not available then set as +5 days from the current date in format DD/MM/YYYY. Example date can be like this - 22/09/2025",
                        )
                        property(
                            name = "stage",
                            type = "enum",
                            description = "Stage of the opportunity",
                            enum = listOf("Propose"),
                            required = true,
                        )
                        additionalProperties = false
                    }

                    additionalProperties = false
                }
            }

        val getPastCallLogsToolDef =
            nativeToolDefinition {
                name(ATOM_PAST_CALLS_LOGS_TOOL_NAME)
                description("This tool can get data about past call logs and opportunities logged for an account in sales force.")
                parameters {
                    property(
                        name = "workflow_id",
                        type = "enum",
                        description = "Id of the workflow to be executed",
                        enum = listOf("0f38396f-8f8b-4ffd-9ca4-4f753162a618"),
                        required = true,
                    )
                    objectProperty(
                        name = "Input",
                        description = "The object that contains information about account to search for call log, opportunity.",
                        required = true,
                    ) {
                        property(
                            name = "search_term",
                            type = "string",
                            description = "The account name for search. For example - BW, Siemens, Volkswagen",
                            required = true,
                        )
                    }
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

    override suspend fun listTools(mcpServerInfo: MCPServerInfo): List<McpToolDefinition> =
        listOf(
            toMcpToolDef(
                callLogUpdateToolDef,
                mcpServerInfo,
            ),
            toMcpToolDef(getPastCallLogsToolDef, mcpServerInfo),
        )

    override suspend fun executeTool(
        tool: ToolDefinition,
        arguments: String,
        paramsAccessor: ToolParamsAccessor?,
        openAIClient: OpenAIClient?,
        headers: Map<String, String>,
        eventEmitter: ((ServerSentEvent<String>) -> Unit)?,
    ): String {
        try {
            val workflowResult = temporalService.runWorkflow(AtomWorkflowInput(arguments))
            return workflowResult.outputPayload
        } catch (e: Exception) {
            log.error(e) { "Error executing atom agent workflow" }
            return """{"error": "Failed to execute workflow", "message": "${e.message}"}"""
        }
    }

    override suspend fun close() {
        log.info { "Nothing to close in ${AtomMcpClient::class.simpleName}" }
    }
}
