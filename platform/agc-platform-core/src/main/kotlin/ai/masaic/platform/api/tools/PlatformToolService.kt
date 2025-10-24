package ai.masaic.platform.api.tools

import ai.masaic.openresponses.api.model.FunctionTool
import ai.masaic.openresponses.api.utils.SupportedToolsFormatter
import ai.masaic.openresponses.tool.*
import ai.masaic.openresponses.tool.mcp.MCPToolExecutor
import ai.masaic.openresponses.tool.mcp.MCPToolRegistry
import ai.masaic.platform.api.user.UserInfoProvider
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.core.io.ResourceLoader
import java.util.*

class PlatformToolService(
    private val mcpToolRegistry: MCPToolRegistry,
    private val mcpToolExecutor: MCPToolExecutor,
    private val resourceLoader: ResourceLoader,
    private val nativeToolRegistry: NativeToolRegistry,
    private val objectMapper: ObjectMapper,
    private val plugableToolAdapter: PlugableToolAdapter,
    private val pluggedToolsRegistry: PluggedToolsRegistry,
) : ToolService(mcpToolRegistry, mcpToolExecutor, resourceLoader, nativeToolRegistry, objectMapper, plugableToolAdapter) {
    private val mapper = jacksonObjectMapper()

    suspend fun derivePluggableTool(functionTool: FunctionTool): FunctionTool? {
        val toolName = functionTool.name ?: return null
        val toolDescription = functionTool.description ?: return null
        // validate whether user id is present in request context
        UserInfoProvider.userId() ?: return null
        val clientSideTool = ClientSideTool.fromFunctionTool(mapper, functionTool, pluggedToolsRegistry.toolKey(toolName))
        return clientSideTool?.let {
            pluggedToolsRegistry.add(clientSideTool)
            clientSideTool.toFunctionTool()
        }
    }
}

data class ClientSideTool(
    override val id: String,
    override val name: String,
    override val description: String,
    override val parameters: MutableMap<String, Any>,
    override val eventMeta: ToolProgressEventMeta? = null,
    val executionAttributes: ToolExecutionAttributes,
) : PlugableToolDefinition(
        id = id,
        name = name,
        description = description,
        parameters = parameters,
        eventMeta = eventMeta,
        type = "client_side",
    ) {
    companion object {
        private const val PROPERTIES = "properties"
        private const val REQUIRED = "required"
        private const val EXECUTION_SPECS_KEY = "execution_specs"

        fun fromFunctionTool(
            mapper: ObjectMapper,
            functionTool: FunctionTool,
            id: String,
        ): ClientSideTool? {
            // 1) Pull out properties map
            val topParams = functionTool.parameters.toMutableMap()
            val props = (topParams[PROPERTIES] as? Map<*, *>)?.toMutableMap() ?: return null

            // 2) Extract execution_specs object (if any)
            val execSpecs = props[EXECUTION_SPECS_KEY] as? Map<*, *> ?: return null

            // 3) Parse raw def
            val specObjectNode = mapper.convertValue<JsonNode>(execSpecs).path("properties")
            val type =
                try {
                    SupportedToolsFormatter.constOrFirstEnum(specObjectNode.path("type"), "type")
                } catch (e: Exception) {
                    throw MultiPlugAdapterException("unable to find the type from $EXECUTION_SPECS_KEY")
                }
            if (type != "client_side") return null

            // 4) Remove execution_specs from properties
            props.remove(EXECUTION_SPECS_KEY)

            // 5) Also remove it from 'required' if present
            val required = (topParams[REQUIRED] as? List<*>)?.toMutableList()
            required?.remove(EXECUTION_SPECS_KEY)

            // 6) Write updated maps back
            topParams[PROPERTIES] = props
            if (required != null) topParams[REQUIRED] = required

            val maxRetryAttempts =
                try {
                    SupportedToolsFormatter.constOrFirstEnum<Int>(specObjectNode.path("maxRetryAttempts"), "maxRetryAttempts")
                } catch (e: Exception) {
                    ToolExecutionAttributes().maxRetryAttempts
                }
            val waitTimeInMillis =
                try {
                    SupportedToolsFormatter.constOrFirstEnum<Long>(specObjectNode.path("waitTimeInMillis"), "waitTimeInMillis")
                } catch (e: Exception) {
                    ToolExecutionAttributes().waitTimeInMillis
                }

            // 7) Build your ClientSideTool with cleaned parameters and strong-typed executionAttributes
            return ClientSideTool(
                id = id,
                name = requireNotNull(functionTool.name) { "FunctionTool.name is null" },
                description = requireNotNull(functionTool.description) { "FunctionTool.description is null" },
                parameters = topParams.toMutableMap(),
                executionAttributes =
                    ToolExecutionAttributes(
                        maxRetryAttempts = maxRetryAttempts,
                        waitTimeInMillis = waitTimeInMillis,
                    ),
                eventMeta = ToolProgressEventMeta(infix = "agc"),
            )
        }
    }
}

data class ToolExecutionAttributes(
    val maxRetryAttempts: Int = 1,
    val waitTimeInMillis: Long = 120_1000,
)
