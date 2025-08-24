package ai.masaic.platform.api.registry.functions

import ai.masaic.openresponses.api.service.ResponseProcessingException
import java.time.Instant

/**
 * Service layer for function registry operations.
 * Handles business logic, validation, and coordinates between repository and validator.
 */
class FunctionRegistryService(
    private val repository: FunctionRegistryRepository,
    private val validator: FunctionRegistryValidator,
) {
    /**
     * Creates a new function in the registry.
     */
    suspend fun createFunction(request: FunctionCreate): FunctionDoc {
        // Validate the request
        val validationResult = validator.validateCreateRequest(request)
        if (validationResult is ValidationResult.Error) {
            throw FunctionRegistryException(validationResult.code, validationResult.message)
        }

        // Create the function document
        val now = Instant.now()
        val function =
            FunctionDoc(
                name = request.name,
                description = request.description,
                runtime = Runtime(Runtime.PYTHON),
                deps = request.deps,
                code = request.code,
                inputSchema = request.inputSchema, // Will be populated by schema inference later
                outputSchema = mutableMapOf(), // Will be populated by schema inference later
                createdAt = now,
                updatedAt = now,
            )

        return repository.save(function)
    }

    suspend fun isFunctionWithNameExists(name: String) = repository.existsByName(name)

    /**
     * Retrieves a function by name.
     */
    suspend fun getFunction(name: String): FunctionDoc =
        repository.findByName(name) 
            ?: throw FunctionRegistryException(
                ErrorCodes.FUNCTION_NOT_FOUND,
                "Function '$name' not found.",
            )

    /**
     * Lists functions with optional search and pagination.
     */
    suspend fun listFunctions(
        query: String? = null,
        limit: Int = 100,
        cursor: String? = null,
        includeCode: Boolean = false,
    ): FunctionListResponse {
        val functions =
            if (query.isNullOrBlank()) {
                repository.findAll(limit, cursor)
            } else {
                repository.searchByName(query, limit)
            }

        val items =
            functions.map { doc ->
                if (includeCode) {
                    // Return full function document
                    FunctionListItem.from(doc.copy(code = doc.code))
                } else {
                    // Return lightweight version without code
                    FunctionListItem.from(doc.copy(code = ""))
                }
            }

        return FunctionListResponse(
            items = items,
            nextCursor = null, // TODO: Implement cursor-based pagination
        )
    }

    /**
     * Updates an existing function.
     */
    suspend fun updateFunction(
        name: String,
        request: FunctionUpdate,
    ): FunctionDoc {
        // Validate the update request
        val validationResult = validator.validateUpdateRequest(request)
        if (validationResult is ValidationResult.Error) {
            throw FunctionRegistryException(validationResult.code, validationResult.message)
        }

        // Check if function exists
        if (!repository.existsByName(name)) {
            throw FunctionRegistryException(
                ErrorCodes.FUNCTION_NOT_FOUND,
                "Function '$name' not found.",
            )
        }

        // Update the function
        val updatedFunction =
            repository.updateByName(name, request)
                ?: throw FunctionRegistryException(
                    "UPDATE_FAILED",
                    "Failed to update function '$name'.",
                )

        return updatedFunction
    }

    /**
     * Deletes a function by name.
     */
    suspend fun deleteFunction(name: String) {
        if (!repository.existsByName(name)) {
            throw FunctionRegistryException(
                ErrorCodes.FUNCTION_NOT_FOUND,
                "Function '$name' not found.",
            )
        }

        val deleted = repository.deleteByName(name)
        if (!deleted) {
            throw FunctionRegistryException(
                "DELETE_FAILED",
                "Failed to delete function '$name'.",
            )
        }
    }

    /**
     * Retrieves all available functions without pagination limits.
     * This is useful for getting a complete list of functions for agent configuration.
     * 
     * @param includeCode Whether to include the function code in the response (default: false for better performance)
     * @return List of all available functions as FunctionListItem objects
     */
    suspend fun getAllAvailableFunctions(includeCode: Boolean = false): List<FunctionListItem> {
        // Get all functions without limits
        val functions = repository.findAll(limit = Int.MAX_VALUE, cursor = null)
        
        return functions.map { doc ->
            if (includeCode) {
                // Return full function document with code
                FunctionListItem.from(doc)
            } else {
                // Return lightweight version without code for better performance
                FunctionListItem.from(doc.copy(code = ""))
            }
        }
    }

    /**
     * Preview function (placeholder for future schema inference).
     */
    suspend fun previewFunction(request: FunctionPreview): Map<String, Any> {
        // TODO: Implement schema inference when ready
        // For now, return empty schemas
        return mapOf(
            "inputSchema" to emptyMap<String, Any>(),
            "outputSchema" to emptyMap<String, Any>(),
        )
    }
}

/**
 * Custom exception for function registry operations.
 */
class FunctionRegistryException(
    val code: String,
    message: String,
) : ResponseProcessingException(message)
