package ai.masaic.openresponses.api.controller

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.model.CreateResponseRequest
import ai.masaic.openresponses.api.model.ResponseInputItemList
import ai.masaic.openresponses.api.service.*
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange

@RestController
@RequestMapping("/v1")
@CrossOrigin("*")
class ResponseController(
    private val responseFacadeService: ResponseFacadeService,
    private val responseStore: ResponseStore,
    private val responseStoreService: ResponseStoreService,
) {
    private val log = LoggerFactory.getLogger(ResponseController::class.java)

    @PostMapping("/responses", produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE])
    suspend fun createResponse(
        @RequestBody request: CreateResponseRequest,
        @RequestHeader headers: MultiValueMap<String, String>,
        @RequestParam queryParams: MultiValueMap<String, String>,
    ): ResponseEntity<*> {
        log.debug("Received response request for model: ${request.model}, streaming: ${request.stream}")

        // Use the facade service to handle validation, formatting, and routing
        val result = responseFacadeService.processResponseForController(request, headers, queryParams)
        return when (result) {
            is Flow<*> -> {
                log.debug("Returning streaming response")
                ResponseEntity
                    .ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(result)
            }
            else -> {
                log.debug("Returning non-streaming response")
                ResponseEntity.ok(result)
            }
        }
    }

    @GetMapping("/responses/{responseId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getResponse(
        @PathVariable responseId: String,
        @RequestHeader headers: MultiValueMap<String, String>,
        @RequestParam queryParams: MultiValueMap<String, String>,
        exchange: ServerWebExchange,
    ): ResponseEntity<*> {
        try {
            return ResponseEntity.ok(responseStoreService.getFormattedResponse(responseId))
        } catch (e: ResponseNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }
    }

    @DeleteMapping("/responses/{responseId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun deleteResponse(
        @PathVariable responseId: String,
    ): ResponseEntity<Map<String, Any>> {
        val deleted = responseStore.deleteResponse(responseId)
        return ResponseEntity.ok(
            mapOf(
                "id" to responseId,
                "deleted" to deleted,
                "object" to "response",
            ),
        )
    }

    @GetMapping("/responses/{responseId}/input_items", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun listInputItems(
        @PathVariable responseId: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "desc") order: String,
        @RequestParam(required = false) after: String?,
        @RequestParam(required = false) before: String?,
    ): ResponseEntity<ResponseInputItemList> =
        try {
            ResponseEntity.ok(responseStoreService.listInputItems(responseId, limit, order, after, before))
        } catch (e: ResponseNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }
}
