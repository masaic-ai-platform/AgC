package dev.langchain4j.mcp.client

import ai.masaic.openresponses.tool.mcp.CallToolResponse
import ai.masaic.openresponses.tool.mcp.McpToolsInputSchemaParsingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.TextContent
import mu.KotlinLogging
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class Converter {
    companion object {
        private val log = KotlinLogging.logger {}

        fun convert(tree: JsonNode): List<ToolSpecification> {
            val array = tree.path("result").path("tools") as ArrayNode
            val result: MutableList<ToolSpecification> = ArrayList()
            for (tool in array) {
                try {
                    val builder = ToolSpecification.builder()
                    builder.name(tool["name"].asText())
                    if (tool.has("description")) {
                        builder.description(tool["description"].asText())
                    }
                    if (tool.has("inputSchema")) {
                        builder.parameters(ToolSpecificationHelper.jsonNodeToJsonSchemaElement(tool["inputSchema"]) as JsonObjectSchema)
                    } else {
                        builder.parameters(
                            JsonObjectSchema
                                .builder() // no fields
                                .build(),
                        )
                    }
                    result.add(builder.build())
                } catch (ex: Exception) {
                    log.warn { "Error occurred while parsing ${tool["name"].asText()}, error: ${ex.printStackTrace()}" }
                    log.warn { "Skipping this tool." }
                }
            }
            return result
        }

        fun extractResult(result: McpSchema.CallToolResult): CallToolResponse =
            try {
                // Extract content from the result
                val content =
                    if (result.content.isNotEmpty()) {
                        result.content.joinToString("\n") { contentItem ->
                            when (contentItem) {
                                is TextContent -> contentItem.text
                                else -> contentItem.toString()
                            }
                        }
                    } else {
                        "No content returned returned by the tool."
                    }

                // Check if this is an error result
                val isError = result.isError ?: false

                // Format error content if needed
                val finalContent =
                    if (isError) {
                        "There was an error executing the tool. The tool returned: $content"
                    } else {
                        content
                    }

                CallToolResponse(isError, finalContent)
            } catch (ex: Exception) {
                log.warn("Error occurred while extracting CallToolResult: ${ex.message}", ex)
                CallToolResponse(true, "There was an error processing the tool result")
            }

        fun extractResult(result: JsonNode): CallToolResponse {
            if (result.has("result")) {
                val resultNode = result["result"]
                if (resultNode.has("content")) {
                    var content = extractSuccessfulResult(resultNode["content"] as ArrayNode)
                    var isError = false
                    if (resultNode.has("isError")) {
                        isError = resultNode["isError"].asBoolean()
                    }

                    if (isError) {
                        content = String.format("There was an error executing the tool. The tool returned: %s", content)
                    }

                    return CallToolResponse(isError, content)
                } else {
                    log.warn("Result does not contain 'content' element: {}", result)
                    return CallToolResponse(true, "There was an error executing the tool")
                }
            } else if (result.has("error")) {
                return CallToolResponse(true, extractError(result["error"]))
            } else {
                log.warn("Result contains neither 'result' nor 'error' element: {}", result)
                return CallToolResponse(true, "There was an error executing the tool")
            }
        }

        private fun extractSuccessfulResult(contents: ArrayNode): String {
            val contentStream = StreamSupport.stream(contents.spliterator(), false)
            return contentStream
                .map { content: JsonNode ->
                    if (content["type"].asText() != "text") {
                        throw RuntimeException("Unsupported content type: " + content["type"].toString())
                    } else {
                        return@map content["text"].asText()
                    }
                }.collect(Collectors.joining("\n")) as String
        }

        private fun extractError(errorNode: JsonNode): String {
            var errorMessage: String? = ""
            if (errorNode["message"] != null) {
                errorMessage = errorNode["message"].asText("")
            }

            var errorCode: Int? = null
            if (errorNode["code"] != null) {
                errorCode = errorNode["code"].asInt()
            }

            log.warn("Result contains an error: {}, code: {}", errorMessage, errorCode)
            return String.format("There was an error executing the tool. Message: %s. Code: %s", errorMessage, errorCode)
        }

        fun toJsonObjectSchema(inputSchema: JsonNode): JsonObjectSchema =
            if (!inputSchema.has("properties") || inputSchema["properties"].isEmpty) {
                JsonObjectSchema.builder().build()
            } else {
                try {
                    ToolSpecificationHelper.jsonNodeToJsonSchemaElement(inputSchema) as JsonObjectSchema
                } catch (ex: Exception) {
                    throw McpToolsInputSchemaParsingException("Error ${ex.message} while parsing schema=$inputSchema", ex)
                }
            }
    }
}
