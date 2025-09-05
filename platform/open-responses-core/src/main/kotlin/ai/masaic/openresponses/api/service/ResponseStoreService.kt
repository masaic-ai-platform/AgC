package ai.masaic.openresponses.api.service

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.model.ResponseInputItemList
import ai.masaic.openresponses.api.utils.PayloadFormatter
import com.fasterxml.jackson.databind.JsonNode
import com.openai.models.responses.Response
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class ResponseStoreService(
    private val responseStore: ResponseStore,
    private val payloadFormatter: PayloadFormatter,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Retrieves a response by ID from the OpenAI API with improved error handling.
     *
     * @param responseId The ID of the response to retrieve
     * @return The response from the OpenAI API
     * @throws ResponseNotFoundException If the response cannot be found
     * @throws ResponseProcessingException If there is an error processing the response
     */
    suspend fun getResponse(
        responseId: String,
    ): Response =
        try {
            responseStore.getResponse(responseId) ?: throw ResponseNotFoundException("Response not found with ID: $responseId")
        } catch (e: Exception) {
            when (e) {
                is ResponseNotFoundException -> throw ResponseNotFoundException("Response with ID $responseId not found")
                else -> throw ResponseProcessingException("Error retrieving response: ${e.message}")
            }
        }

    suspend fun getFormattedResponse(
        responseId: String,
    ): JsonNode = payloadFormatter.formatResponse(getResponse(responseId))

    suspend fun deleteResponse(responseId: String): Boolean = responseStore.deleteResponse(responseId)

    /**
     * Lists input items for a response with pagination.
     *
     * @param responseId The ID of the response
     * @param limit Maximum number of items to return
     * @param order Sort order (asc or desc)
     * @param after Item ID to list items after
     * @param before Item ID to list items before
     * @return List of input items
     * @throws ResponseNotFoundException If the response cannot be found
     */
    suspend fun listInputItems(
        responseId: String,
        limit: Int,
        order: String,
        after: String?,
        before: String?,
    ): ResponseInputItemList {
        logger.info { "Listing input items for response ID: $responseId" }

        val validLimit = limit.coerceIn(1, 100)
        val validOrder = if (order in listOf("asc", "desc")) order else "asc"

        // First check if response exists
        responseStore.getResponse(responseId) ?: throw ResponseNotFoundException("Response not found with ID: $responseId")

        // Get input items from the response store
        val inputItems = responseStore.getInputItems(responseId)

        val sortedItems =
            if (validOrder == "asc") {
                inputItems.sortedBy { it.createdAt }
            } else {
                inputItems.sortedByDescending { it.createdAt }
            }

        val fromIndex = after?.let { id -> sortedItems.indexOfFirst { it.id == id } + 1 } ?: 0
        val toIndex = before?.let { id -> sortedItems.indexOfFirst { it.id == id } } ?: sortedItems.size

        val validFromIndex = fromIndex.coerceIn(0, sortedItems.size)
        val validToIndex = toIndex.coerceIn(validFromIndex, sortedItems.size)

        val paginatedItems = sortedItems.subList(validFromIndex, validToIndex).take(validLimit)

        val hasMore = (validToIndex - validFromIndex) > paginatedItems.size
        val firstId = paginatedItems.firstOrNull()?.id
        val lastId = paginatedItems.lastOrNull()?.id

        return ResponseInputItemList(
            data = paginatedItems,
            hasMore = hasMore,
            firstId = firstId,
            lastId = lastId,
        )
    }
}
