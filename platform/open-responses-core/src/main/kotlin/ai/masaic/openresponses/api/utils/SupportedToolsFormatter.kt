package ai.masaic.openresponses.api.utils

import ai.masaic.openresponses.api.model.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class SupportedToolsFormatter {
    companion object {
        private val mapper = jacksonObjectMapper()

        fun buildToolDef(completionFunction: CompletionFunction): Tool? {
            val params = mapper.readTree(mapper.writeValueAsString(completionFunction.functionTool.parameters)).path("properties") // root.path("function").path("parameters").path("properties")
            if (params.isMissingNode || !params.isObject) {
                error("Invalid tool definition: missing function.parameters.properties")
            }

            return when (val type = constOrFirstEnum(params.path("type"), "type")) {
                "mcp" -> buildMcpToolDef(type, params)
                "file_search" -> buildFileSearchToolDef(type, completionFunction.functionTool, params)
                "py_fun_tool" -> buildPyFunToolDef(type, params)
                else -> null
            }
        }

        private fun buildMcpToolDef(
            type: String,
            params: JsonNode,
        ): MCPTool {
            val serverLabel = constOrFirstEnum(params.path("server_label"), "server_label")
            val serverUrl = constOrFirstEnum(params.path("server_url"), "server_url")

            val allowedTools =
                run {
                    val enumArr = params.path("allowed_tools").path("items").path("enum")
                    if (!enumArr.isArray || enumArr.size() == 0) {
                        emptyList<String>()
                    }
                    enumArr.map { it.asText() }
                }

            val headersSchema = params.path("headers")
            val headers = parseHeadersFromSchema(headersSchema)

            return MCPTool(
                type = type,
                serverLabel = serverLabel,
                serverUrl = serverUrl,
                allowedTools = allowedTools,
                headers = headers,
            )
        }

        private fun buildFileSearchToolDef(
            type: String,
            functionTool: CompletionFunctionTool,
            params: JsonNode,
        ): FileSearchTool {
            require(!functionTool.name.isNullOrBlank()) { "name is mandatory, provide name of the tool." }
            require(!functionTool.description.isNullOrBlank()) { "description is mandatory, provide description of the tool." }
            val vectorStoreIds =
                run {
                    val enumArr = params.path("vector_store_ids").path("items").path("enum")
                    if (!enumArr.isArray || enumArr.size() == 0) {
                        error("vector_store_ids enum missing in schema")
                    }
                    enumArr.map { it.asText() }
                }

            val modelInfo =
                run {
                    val inforObj = params.path("modelInfo").path("properties")
                    val bearerToken = constOrFirstEnum(inforObj.path("bearerToken"), "bearerToken")
                    val model = constOrFirstEnum(inforObj.path("model"), "model")
                    ModelInfo(bearerToken, model)
                }

            return FileSearchTool(
                type = type,
                vectorStoreIds = vectorStoreIds,
                modelInfo = modelInfo,
                alias = functionTool.name,
                description = functionTool.description,
            )
        }

        private fun buildPyFunToolDef(
            type: String,
            params: JsonNode,
        ): PyFunTool {
            val code = constOrFirstEnum(params.path("code"), "code")
            val toolDef =
                run {
                    val toolDefObj = params.path("tool_def").path("properties")
                    val name = constOrFirstEnum(toolDefObj.path("name"), "name")
                    val description = constOrFirstEnum(toolDefObj.path("description"), "description")
                    val parametersNode = toolDefObj.path("parameters").toString()
                    val parametersMap: MutableMap<String, Any> = if (parametersNode.isEmpty()) mutableMapOf() else mapper.readValue(parametersNode)
                    FunctionDetails(name = name, description = description, parameters = parametersMap)
                }

            val codeInterpreter =
                run {
                    val obj = params.path("code_interpreter").path("properties")
                    val serverLabel = constOrFirstEnum(obj.path("server_label"), "server_label")
                    val url = constOrFirstEnum(obj.path("url"), "url")
                    val apiKey = constOrFirstEnum(obj.path("apiKey"), "apiKey")
                    PyInterpreterServer(serverLabel = serverLabel, url = url, apiKey = apiKey)
                }

            return PyFunTool(
                type = type,
                functionDetails = toolDef,
                code = code,
                interpreterServer = codeInterpreter,
            )
        }

        private fun constOrFirstEnum(
            node: JsonNode,
            field: String,
        ): String {
            val constNode = node.path("const")
            if (!constNode.isMissingNode && !constNode.isNull) return constNode.asText()
            val enumNode = node.path("enum")
            if (enumNode.isArray && enumNode.size() > 0) return enumNode[0].asText()
            error("Missing const/enum for '$field' in schema")
        }

        private fun valueFromConstOrEnum(
            node: JsonNode,
            fieldPath: String,
        ): String {
            val c = node.path("const")
            if (!c.isMissingNode && !c.isNull) return c.asText()
            val e = node.path("enum")
            if (e.isArray && e.size() > 0) return e[0].asText()
            error("Missing const/enum for '$fieldPath' in headers schema")
        }

        private fun parseHeadersFromSchema(headersSchema: JsonNode): Map<String, String> {
            if (headersSchema.isMissingNode || headersSchema.isNull) return emptyMap()

            // Case 1: headers.const is an object with concrete values
            val constNode = headersSchema.path("const")
            if (constNode.isObject) {
                return constNode.fields().asSequence().associate { it.key to it.value.asText() }
            }

            // Case 2: per-key definitions under properties
            val propsNode = headersSchema.path("properties")
            val reqNode = headersSchema.path("required")

            if (propsNode.isObject) {
                val result = mutableMapOf<String, String>()

                // Gather required keys (to enforce fixed values)
                val requiredKeys: Set<String> =
                    if (reqNode.isArray) reqNode.map { it.asText() }.toSet() else emptySet()

                // For each defined property, take const or first enum if available
                val it = propsNode.fields()
                while (it.hasNext()) {
                    val (key, schemaNode) = it.next()
                    // Only map if schema pins a value (const or enum)
                    val hasConst = !schemaNode.path("const").isMissingNode && !schemaNode.path("const").isNull
                    val enumArr = schemaNode.path("enum")
                    val hasEnum = enumArr.isArray && enumArr.size() > 0

                    if (hasConst || hasEnum) {
                        result[key] = valueFromConstOrEnum(schemaNode, "headers.properties.$key")
                    } else if (key in requiredKeys) {
                        // Required but no fixed value → fail fast (no defaults)
                        error("Required header '$key' lacks const/enum in headers schema")
                    }
                    // If not required and no fixed value: ignore (no defaults)
                }

                // Ensure all required keys are present in result
                for (rk in requiredKeys) {
                    if (!result.containsKey(rk)) {
                        // Might be that required key isn't even in properties → fail
                        error("Required header '$rk' missing from headers.properties or lacks const/enum")
                    }
                }
                return result.toMap()
            }

            // No const object and no properties → nothing concrete to map
            return emptyMap()
        }
    }
}
