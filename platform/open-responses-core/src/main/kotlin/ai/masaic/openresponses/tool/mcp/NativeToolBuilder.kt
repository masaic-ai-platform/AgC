package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.tool.NativeToolDefinition
import ai.masaic.openresponses.tool.ToolProgressEventMeta

/**
 * Main DSL entry point.
 *
 * Usage:
 *   val tool = nativeToolDefinition {
 *       name("FunctionRequirementGatherer")
 *       description("Gathers user requirements for a function to be generated and produces a string output.")
 *       eventMeta(ToolProgressEventMeta("generating_function"))  // optional
 *       parameters {
 *           property(
 *               name = "userMessage",
 *               type = "string",
 *               description = "String parameter to feed in the user requirement message for the function.",
 *               required = true
 *           )
 *           objectProperty(
 *               name = "agent",
 *               description = "Platform agent configuration",
 *               required = true
 *           ) {
 *               property("name", "string", "Agent name", required = true)
 *               nullableProperty("model", "string", "Model to use")
 *               arrayProperty("tools", "Available tools") {
 *                   itemsOneOf("MCPTool", "PyFunTool")
 *               }
 *           }
 *           definition("MCPTool") {
 *               property("type", "string", required = true)
 *               property("server_label", "string", required = true)
 *               property("server_url", "string", required = true)
 *               arrayProperty("allowed_tools", default = emptyList<String>()) {
 *                   items("string")
 *               }
 *           }
 *           additionalProperties = false   // default is false; change if you need it
 *       }
 *   }
 */
fun nativeToolDefinition(init: NativeToolBuilder.() -> Unit): NativeToolDefinition = NativeToolBuilder().apply(init).build()

/**
 * Builder for NativeToolDefinition.
 */
class NativeToolBuilder {
    private var name: String? = null
    private var description: String? = null
    private var parameters: MutableMap<String, Any>? = null
    private var eventMeta: ToolProgressEventMeta? = null

    fun name(value: String) = apply { name = value }

    fun description(value: String) = apply { description = value }

    fun eventMeta(value: ToolProgressEventMeta) = apply { eventMeta = value }

    fun parameters(init: ParameterSchemaBuilder.() -> Unit) =
        apply {
            parameters = ParameterSchemaBuilder().apply(init).build()
        }

    fun build(): NativeToolDefinition =
        NativeToolDefinition(
            name = requireNotNull(name) { "Tool name is required." },
            description = requireNotNull(description) { "Tool description is required." },
            parameters =
                parameters ?: mutableMapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>(),
                    "additionalProperties" to false,
                ),
            eventMeta = eventMeta,
        )
}

/**
 * Builder for the JSON-schema-like `parameters` block.
 */
class ParameterSchemaBuilder {
    private val properties = mutableMapOf<String, Any>()
    private val requiredProps = mutableSetOf<String>()
    private val definitions = mutableMapOf<String, Any>()
    var additionalProperties: Boolean = false

    /**
     * Define a simple property.
     */
    fun property(
        name: String,
        type: String,
        description: String,
        required: Boolean = false,
    ) = apply {
        properties[name] =
            mapOf(
                "type" to type,
                "description" to description,
            )
        if (required) requiredProps += name
    }

    /**
     * Define an object property with nested schema.
     */
    fun objectProperty(
        name: String,
        description: String? = null,
        required: Boolean = false,
        init: ObjectSchemaBuilder.() -> Unit,
    ) = apply {
        val schema = ObjectSchemaBuilder().apply(init).build()
        if (description != null) {
            schema["description"] = description
        }
        properties[name] = schema
        if (required) requiredProps += name
    }

    /**
     * Define an array property.
     */
    fun arrayProperty(
        name: String,
        description: String? = null,
        required: Boolean = false,
        init: ArraySchemaBuilder.() -> Unit,
    ) = apply {
        val schema = ArraySchemaBuilder().apply(init).build()
        if (description != null) {
            schema["description"] = description
        }
        properties[name] = schema
        if (required) requiredProps += name
    }

    /**
     * Define a property with nullable type.
     */
    fun nullableProperty(
        name: String,
        type: String,
        description: String,
        required: Boolean = false,
    ) = apply {
        properties[name] =
            mapOf(
                "type" to listOf(type, "null"),
                "description" to description,
            )
        if (required) requiredProps += name
    }

    /**
     * Define a property that references a definition.
     */
    fun refProperty(
        name: String,
        ref: String,
        description: String? = null,
        required: Boolean = false,
    ) = apply {
        val schema = mutableMapOf<String, Any>("\$ref" to "#/definitions/$ref")
        if (description != null) {
            schema["description"] = description
        }
        properties[name] = schema
        if (required) requiredProps += name
    }

    /**
     * Add a definition for reusable schema components.
     */
    fun definition(
        name: String,
        init: ObjectSchemaBuilder.() -> Unit,
    ) = apply {
        definitions[name] = ObjectSchemaBuilder().apply(init).build()
    }

    /**
     * Produce the parameters map in the exact shape
     * expected by OpenAI function-calling.
     */
    fun build(): MutableMap<String, Any> {
        val result =
            mutableMapOf<String, Any>(
                "type" to "object",
                "properties" to properties,
                "required" to requiredProps.toList(),
                "additionalProperties" to additionalProperties,
            )
        
        if (definitions.isNotEmpty()) {
            result["definitions"] = definitions
        }
        
        return result
    }
}

/**
 * Builder for object schema definitions.
 */
class ObjectSchemaBuilder {
    private val properties = mutableMapOf<String, Any>()
    private val requiredProps = mutableSetOf<String>()
    var additionalProperties: Boolean = false

    fun property(
        name: String,
        type: String,
        description: String? = null,
        default: Any? = null,
        enum: List<Any>? = null,
        required: Boolean = false,
    ) = apply {
        val schema = mutableMapOf<String, Any>("type" to type)
        description?.let { schema["description"] = it }
        default?.let { schema["default"] = it }
        enum?.let { schema["enum"] = it }
        
        properties[name] = schema
        if (required) requiredProps += name
    }

    fun nullableProperty(
        name: String,
        type: String,
        description: String? = null,
        default: Any? = null,
        required: Boolean = false,
    ) = apply {
        val schema = mutableMapOf<String, Any>("type" to listOf(type, "null"))
        description?.let { schema["description"] = it }
        default?.let { schema["default"] = it }
        
        properties[name] = schema
        if (required) requiredProps += name
    }

    fun refProperty(
        name: String,
        ref: String,
        description: String? = null,
        required: Boolean = false,
    ) = apply {
        val schema = mutableMapOf<String, Any>("\$ref" to "#/definitions/$ref")
        description?.let { schema["description"] = it }
        
        properties[name] = schema
        if (required) requiredProps += name
    }

    fun arrayProperty(
        name: String,
        description: String? = null,
        default: Any? = null,
        required: Boolean = false,
        init: ArraySchemaBuilder.() -> Unit,
    ) = apply {
        val schema = ArraySchemaBuilder().apply(init).build().toMutableMap()
        description?.let { schema["description"] = it }
        default?.let { schema["default"] = it }
        
        properties[name] = schema
        if (required) requiredProps += name
    }

    fun objectProperty(
        name: String,
        description: String? = null,
        default: Any? = null,
        required: Boolean = false,
        additionalProps: Boolean = false,
        init: ObjectSchemaBuilder.() -> Unit,
    ) = apply {
        val nestedBuilder = ObjectSchemaBuilder().apply { additionalProperties = additionalProps }.apply(init)
        val schema = nestedBuilder.build().toMutableMap()
        description?.let { schema["description"] = it }
        default?.let { schema["default"] = it }
        
        properties[name] = schema
        if (required) requiredProps += name
    }

    fun build(): MutableMap<String, Any> =
        mutableMapOf(
            "type" to "object",
            "properties" to properties,
            "required" to requiredProps.toList(),
            "additionalProperties" to additionalProperties,
        )
}

/**
 * Builder for array schema definitions.
 */
class ArraySchemaBuilder {
    private var itemsSchema: Any? = null

    fun items(type: String) =
        apply {
            itemsSchema = mapOf("type" to type)
        }

    fun itemsObject(init: ObjectSchemaBuilder.() -> Unit) =
        apply {
            itemsSchema = ObjectSchemaBuilder().apply(init).build()
        }

    fun itemsRef(ref: String) =
        apply {
            itemsSchema = mapOf("\$ref" to "#/definitions/$ref")
        }

    fun itemsOneOf(vararg refs: String) =
        apply {
            itemsSchema = mapOf("oneOf" to refs.map { mapOf("\$ref" to "#/definitions/$it") })
        }

    fun build(): MutableMap<String, Any> {
        val result = mutableMapOf<String, Any>("type" to "array")
        itemsSchema?.let { result["items"] = it }
        return result
    }
}
