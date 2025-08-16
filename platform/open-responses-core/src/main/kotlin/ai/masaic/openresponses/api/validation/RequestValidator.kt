package ai.masaic.openresponses.api.validation

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.exception.VectorStoreNotFoundException
import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.search.VectorStoreService
import ai.masaic.openresponses.tool.ToolDefinition
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Validator for completion and response requests.
 */
@Profile("!platform")
@Component
open class RequestValidator(
    private val vectorStoreService: VectorStoreService,
    private val responseStore: ResponseStore,
) {
    protected val modelPattern = Regex(".+@.+")
    protected val imageApiKey = System.getenv("OPEN_RESPONSES_IMAGE_GENERATION_API_KEY")
    protected val imageBaseUrl = System.getenv("OPEN_RESPONSES_IMAGE_GENERATION_BASE_URL")

    fun validateCompletionRequest(request: CreateCompletionRequest) {
        if (!modelPattern.matches(request.model)) {
            throw IllegalArgumentException("model must be in provider@model format")
        }
        if (request.messages.isEmpty()) {
            throw IllegalArgumentException("messages field is required")
        }
        if (request.metadata != null && !request.store) {
            throw IllegalArgumentException("metadata is only allowed when store=true")
        }
    }

    suspend fun validateResponseRequest(request: CreateResponseRequest) {
        if (!modelPattern.matches(request.model)) {
            throw IllegalArgumentException("model must be in provider@model format")
        }

        val input = request.input
        if (input is String && input.isBlank()) {
            throw IllegalArgumentException("input field is required")
        }

        if (input is List<*> && input.isEmpty()) {
            throw IllegalArgumentException("input field is required")
        }

        if (request.metadata != null && !request.store) {
            throw IllegalArgumentException("metadata is only allowed when store=true")
        }
        if (request.previousResponseId != null) {
            responseStore.getResponse(request.previousResponseId)
                ?: throw IllegalArgumentException("previous_response_id not found")
        }
        request.tools?.forEach { tool -> validateTool(tool) }
    }

    suspend fun validateTool(tool: Tool) {
        when (tool) {
            is ImageGenerationTool -> {
                if (imageBaseUrl.isNullOrBlank()) {
                    if (!modelPattern.matches(tool.model)) {
                        throw IllegalArgumentException("Image model must be in provider@model format")
                    }
                }
                if (imageApiKey.isNullOrBlank() && tool.modelProviderKey.isNullOrBlank()) {
                    throw IllegalArgumentException("model_provider_key is required for image generation")
                }
            }
            is FileSearchTool -> {
                tool.vectorStoreIds?.forEach { id ->
                    ensureVectorStoreExists(id)
                }
            }
            is AgenticSeachTool -> {
                tool.vectorStoreIds?.forEach { id ->
                    ensureVectorStoreExists(id)
                }
            }
            is PyFunTool -> {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "${tool.platformToolName()} is not supported outside platform deployment."
                )
            }
        }
    }

    suspend fun ensureVectorStoreExists(id: String) {
        try {
            vectorStoreService.getVectorStore(id)
        } catch (e: VectorStoreNotFoundException) {
            throw IllegalArgumentException("Vector store not found: $id")
        }
    }
}
