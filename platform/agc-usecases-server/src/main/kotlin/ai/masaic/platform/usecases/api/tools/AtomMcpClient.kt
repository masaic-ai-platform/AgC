package ai.masaic.platform.usecases.api.tools

import ai.masaic.openresponses.tool.*
import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import ai.masaic.openresponses.tool.mcp.McpClient
import ai.masaic.openresponses.tool.mcp.McpToolDefinition
import ai.masaic.openresponses.tool.mcp.nativeToolDefinition
import ai.masaic.platform.api.utils.JsonSchemaMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.client.OpenAIClient
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent

class AtomMcpClient : McpClient {
    private val log = KotlinLogging.logger { }
    private val mapper = jacksonObjectMapper()

    companion object {
        const val ATOM_TOOL_NAME = "atom_agent"
        val atomToolDef =
            nativeToolDefinition {
                name(ATOM_TOOL_NAME)
                description("atom agent runs the workflow to fetch the require information.")
                parameters {
                    property(
                        name = "workflowName",
                        type = "string",
                        description = "name of the workflow to be executed by atom agent.",
                        required = true,
                    )
                    additionalProperties = false
                }
                eventMeta(ToolProgressEventMeta("atom_agent"))
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
            )
        }
    }

    override suspend fun listTools(mcpServerInfo: MCPServerInfo): List<McpToolDefinition> = listOf(toMcpToolDef(atomToolDef, mcpServerInfo))

    override suspend fun executeTool(
        tool: ToolDefinition,
        arguments: String,
        paramsAccessor: ToolParamsAccessor?,
        openAIClient: OpenAIClient?,
        headers: Map<String, String>,
        eventEmitter: ((ServerSentEvent<String>) -> Unit)?,
    ): String {
        val argTree = mapper.readTree(arguments)
        val toolResult = "Hello world."
        log.info { "ToolResult: $toolResult" }
        return toolResult
    }

    override suspend fun close() {
        log.info { "Nothing to close in ${AtomMcpClient::class.simpleName}" }
    }
}
