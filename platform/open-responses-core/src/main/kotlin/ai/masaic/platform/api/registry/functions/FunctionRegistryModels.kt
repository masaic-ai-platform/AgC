package ai.masaic.platform.api.registry.functions

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Function document stored in MongoDB.
 * Matches the minimal specification without schema inference.
 */
@Document(collection = "function_registry")
data class FunctionDoc(
    @Id
    val name: String,
    val description: String,
    val runtime: Runtime,
    val deps: List<String>,
    val code: String,
    val inputSchema: Map<String, Any>? = null,
    val outputSchema: Map<String, Any>? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Runtime configuration for the function.
 */
data class Runtime(
    val kind: String,
) {
    companion object {
        const val PYTHON = "python"
    }
}

/**
 * Request model for creating a function.
 */
data class FunctionCreate(
    val name: String,
    val description: String,
    val deps: List<String>,
    val code: String,
)

/**
 * Request model for updating a function.
 */
data class FunctionUpdate(
    val description: String? = null,
    val deps: List<String>? = null,
    val code: String? = null,
)

/**
 * Response model for listing functions.
 */
data class FunctionListResponse(
    val items: List<FunctionListItem>,
    val nextCursor: String? = null,
)

/**
 * Lightweight function item for list responses.
 */
data class FunctionListItem(
    val name: String,
    val description: String,
    val runtime: Runtime,
    val deps: List<String>,
    val inputSchema: Map<String, Any>? = null,
    val outputSchema: Map<String, Any>? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(doc: FunctionDoc): FunctionListItem =
            FunctionListItem(
                name = doc.name,
                description = doc.description,
                runtime = doc.runtime,
                deps = doc.deps,
                inputSchema = doc.inputSchema,
                outputSchema = doc.outputSchema,
                createdAt = doc.createdAt,
                updatedAt = doc.updatedAt,
            )
    }
}

/**
 * Request model for previewing schema inference (placeholder for future implementation).
 */
data class FunctionPreview(
    val deps: List<String>,
    val code: String,
)

/**
 * Error response models.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val hints: List<String>? = null,
)

object ErrorCodes {
    const val NAME_CONFLICT = "NAME_CONFLICT"
    const val INVALID_CODE = "INVALID_CODE"
    const val INVALID_NAME = "INVALID_NAME"
    const val INVALID_DEPS = "INVALID_DEPS"
    const val FUNCTION_NOT_FOUND = "FUNCTION_NOT_FOUND"
}
