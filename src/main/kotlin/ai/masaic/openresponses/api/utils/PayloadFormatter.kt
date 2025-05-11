package ai.masaic.openresponses.api.utils

import ai.masaic.openresponses.api.model.CreateCompletionRequest
import ai.masaic.openresponses.api.model.CreateResponseRequest
import ai.masaic.openresponses.api.model.MasaicManagedTool
import ai.masaic.openresponses.api.model.Tool
import ai.masaic.openresponses.tool.ToolService
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.openai.core.JsonValue
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseStreamEvent
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Optional

@Component
class PayloadFormatter(
    private val toolService: ToolService,
    private val mapper: ObjectMapper,
) {
    internal fun formatResponseRequest(request: CreateResponseRequest) {
        request.tools = updateToolsInRequest(request.tools)
    }

    internal fun formatCompletionRequest(request: CreateCompletionRequest) {
        request.tools = updateToolsInCompletionRequest(request.tools)
    }

    /**
     * Updates the tools in the request with proper tool definitions from the tool service.
     *
     * @param tools The original list of tools in the request
     * @return The updated list of tools
     */
    private fun updateToolsInRequest(tools: List<Tool>?): MutableList<Tool>? =
        tools
            ?.map { tool ->
                if (tool is MasaicManagedTool) {
                    toolService.getFunctionTool(tool.type) ?: throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Define tool ${tool.type} properly",
                    )
                } else {
                    tool
                }
            }?.toMutableList()

    /**
     * Updates the tools in the request with proper tool definitions from the tool service.
     *
     * @param tools The original list of tools in the request
     * @return The updated list of tools
     */
    private fun updateToolsInCompletionRequest(tools: List<Map<String, Any>>?): List<Map<String, Any>>? {
        val typeReference = object : TypeReference<List<Map<String, Any>>>() {}
        val tools =
            tools
                ?.filter { it["type"] != "function" }
                ?.map { tool ->
                    val toolMeta = Optional.ofNullable(toolService.getAvailableTool(tool["type"].toString()))
                    if (toolMeta.isPresent) {
                        val completionTool =
                            toolService.getChatCompletionTool(tool["type"].toString()) ?: throw ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Define tool ${tool["type"]} properly",
                            )
                        completionTool
                            .toBuilder()
                            .putAllAdditionalProperties(
                                tool.mapValues {
                                    JsonValue.from(it.value)
                                },
                            ).build()
                    } else {
                        tool
                    }
                }?.toMutableList()
                ?.plus(tools.filter { it["type"] == "function" })

        return mapper.convertValue(tools, typeReference)
    }

    /**
     * Updates the tools in the response to replace function tools with Masaic managed tools.
     *
     * @param response The response to update
     * @return Updated JSON representation of the response
     */
    internal fun formatResponse(response: Response): JsonNode {
        // Convert the Response object to a mutable JSON tree
        val rootNode = mapper.valueToTree<JsonNode>(response) as ObjectNode
        return formatResponseNode(rootNode)
    }

    private fun updateDoubleFormat(rootNode: ObjectNode) {
        val createdAtNode = rootNode.get("created_at")
        if (createdAtNode?.isDouble == true) {
            rootNode.put("created_at", BigDecimal.valueOf(createdAtNode.doubleValue()))
        }
    }

    internal fun formatResponseStreamEvent(event: ResponseStreamEvent): JsonNode {
        if (event.isCompleted()) {
            val responseNode = mapper.valueToTree<JsonNode>(event.asCompleted().response()) as ObjectNode
            formatResponseNode(responseNode)
            val rootNode = mapper.valueToTree<JsonNode>(event) as ObjectNode
            rootNode.replace("response", responseNode)
            return rootNode
        } else if (event.isCreated()) {
            val responseNode = formatResponseNode(mapper.valueToTree<JsonNode>(event.asCreated().response()) as ObjectNode)
            val rootNode = mapper.valueToTree<JsonNode>(event) as ObjectNode
            rootNode.replace("response", responseNode)
            return rootNode
        } else if (event.isInProgress()) {
            val responseNode = mapper.valueToTree<JsonNode>(event.asInProgress().response()) as ObjectNode
            formatResponseNode(responseNode)
            val rootNode = mapper.valueToTree<JsonNode>(event) as ObjectNode
            rootNode.replace("response", responseNode)
            return rootNode
        } else if (event.isFailed()) {
            val responseNode = mapper.valueToTree<JsonNode>(event.asFailed().response()) as ObjectNode
            formatResponseNode(responseNode)
            val rootNode = mapper.valueToTree<JsonNode>(event) as ObjectNode
            rootNode.replace("response", responseNode)
            return rootNode
        }

        val rootNode = mapper.valueToTree<JsonNode>(event) as ObjectNode
        updateDoubleFormat(rootNode)
        return rootNode
    }

    private fun formatResponseNode(rootNode: ObjectNode): ObjectNode {
        updateToolsInResponseJson(rootNode)
        updateDoubleFormat(rootNode)
        return rootNode
    }

    /**
     * Updates the tools array in the response JSON.
     *
     * @param rootNode The root JSON node of the response
     */
    private fun updateToolsInResponseJson(rootNode: ObjectNode) {
        // Get the "tools" array node (if present)
        val toolsNode = rootNode.get("tools") as? ArrayNode ?: return

        // Iterate over each tool node in the array
        for (i in 0 until toolsNode.size()) {
            val toolNode = toolsNode.get(i) as? ObjectNode ?: continue

            // Check if this tool is a function tool by looking at its "type" field.
            if (isToolNodeAFunction(toolNode)) {
                replaceFunctionToolWithMasaicTool(toolsNode, toolNode, i)
            }
        }
    }

    /**
     * Checks if the given tool node is a function tool.
     *
     * @param toolNode The tool node to check
     * @return true if it's a function tool, false otherwise
     */
    private fun isToolNodeAFunction(toolNode: ObjectNode): Boolean =
        toolNode.has("type") &&
            toolNode.get("type").asText() == "function" &&
            toolNode.has("name")

    /**
     * Replaces a function tool with a Masaic managed tool if it's registered.
     *
     * @param toolsNode The array of tools
     * @param toolNode The current tool node
     * @param index The index of the current tool in the array
     */
    private fun replaceFunctionToolWithMasaicTool(
        toolsNode: ArrayNode,
        toolNode: ObjectNode,
        index: Int,
    ) {
        val functionName = toolNode.get("name").asText()
        // Use your toolService to check if this function should be modified
        val toolMetadata = toolService.getAvailableTool(functionName)
        if (toolMetadata != null) {
            // Create a new ObjectNode with only the "type" field set to the function name.
            // This satisfies your requirement to include only the type parameter.
            val newToolNode = mapper.createObjectNode()
            newToolNode.put("type", functionName)
            // Replace the current tool node with the new one.
            toolsNode.set(index, newToolNode)
        }
    }
}
