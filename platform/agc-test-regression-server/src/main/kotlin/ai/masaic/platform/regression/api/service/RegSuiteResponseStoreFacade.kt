package ai.masaic.platform.regression.api.service

import ai.masaic.openresponses.api.model.ResponseInputItemList
import ai.masaic.openresponses.api.service.ResponseNotFoundException
import ai.masaic.openresponses.api.service.ResponseStoreService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.models.responses.Response
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono

/**
 * Facade for accessing response store either in-memory or via HTTP API.
 * This allows the regression server to work against both embedded AgC and remote AgC instances.
 */
@Service
class RegSuiteResponseStoreFacade(
    private val responseStoreService: ResponseStoreService,
    @Value("\${platform.regression.response-store.mode:in-memory}") private val mode: String,
    @Value("\${platform.regression.response-store.api-base-url:http://localhost:6644}") private val apiBaseUrl: String,
) {
    private val log = KotlinLogging.logger { }
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val webClient: WebClient = WebClient.builder().baseUrl(apiBaseUrl).build()

    /**
     * Lists input items for a response.
     * Uses either in-memory service or HTTP API based on configuration.
     */
    suspend fun listInputItems(
        responseId: String,
        limit: Int,
        order: String,
        after: String?,
        before: String?,
    ): ResponseInputItemList {
        return when (mode) {
            "in-memory" -> {
                log.debug { "Using in-memory ResponseStoreService for listInputItems" }
                responseStoreService.listInputItems(responseId, limit, order, after, before)
            }
            "http" -> {
                log.debug { "Using HTTP API for listInputItems from $apiBaseUrl" }
                listInputItemsViaHttp(responseId, limit, order, after, before)
            }
            else -> {
                log.warn { "Unknown mode: $mode, defaulting to in-memory" }
                responseStoreService.listInputItems(responseId, limit, order, after, before)
            }
        }
    }

    /**
     * Gets a response by ID.
     * Uses either in-memory service or HTTP API based on configuration.
     */
    suspend fun getResponse(responseId: String): Response {
        return when (mode) {
            "in-memory" -> {
                log.debug { "Using in-memory ResponseStoreService for getResponse" }
                responseStoreService.getResponse(responseId)
            }
            "http" -> {
                log.debug { "Using HTTP API for getResponse from $apiBaseUrl" }
                getResponseViaHttp(responseId)
            }
            else -> {
                log.warn { "Unknown mode: $mode, defaulting to in-memory" }
                responseStoreService.getResponse(responseId)
            }
        }
    }

    private suspend fun listInputItemsViaHttp(
        responseId: String,
        limit: Int,
        order: String,
        after: String?,
        before: String?,
    ): ResponseInputItemList {
        try {
            val uri = "/v1/responses/$responseId/input_items"
            log.info { "Fetching input items via HTTP: $apiBaseUrl$uri" }

            val response = webClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path(uri)
                        .queryParam("limit", limit)
                        .queryParam("order", order)
                    after?.let { uriBuilder.queryParam("after", it) }
                    before?.let { uriBuilder.queryParam("before", it) }
                    uriBuilder.build()
                }
                .retrieve()
                .onStatus({ status -> status == HttpStatus.NOT_FOUND }) {
                    Mono.error(ResponseNotFoundException("Response not found with ID: $responseId"))
                }
                .onStatus({ status -> status.isError }) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).flatMap { errorBody ->
                        Mono.error(RuntimeException("HTTP error fetching input items: ${clientResponse.statusCode()} - $errorBody"))
                    }
                }
                .awaitBody<String>()

            return mapper.readValue(response)
        } catch (e: Exception) {
            when (e) {
                is ResponseNotFoundException -> throw e
                else -> {
                    log.error(e) { "Error fetching input items via HTTP for responseId: $responseId" }
                    throw RuntimeException("Failed to fetch input items via HTTP: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun getResponseViaHttp(responseId: String): Response {
        try {
            val uri = "/v1/responses/$responseId"
            log.info { "Fetching response via HTTP: $apiBaseUrl$uri" }

            val responseJson = webClient.get()
                .uri(uri)
                .retrieve()
                .onStatus({ status -> status == HttpStatus.NOT_FOUND }) {
                    Mono.error(ResponseNotFoundException("Response not found with ID: $responseId"))
                }
                .onStatus({ status -> status.isError }) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).flatMap { errorBody ->
                        Mono.error(RuntimeException("HTTP error fetching response: ${clientResponse.statusCode()} - $errorBody"))
                    }
                }
                .awaitBody<String>()

            // Parse the JSON response to Response object using Jackson ObjectMapper
            return mapper.readValue(responseJson, Response::class.java)
        } catch (e: Exception) {
            when (e) {
                is ResponseNotFoundException -> throw e
                else -> {
                    log.error(e) { "Error fetching response via HTTP for responseId: $responseId" }
                    throw RuntimeException("Failed to fetch response via HTTP: ${e.message}", e)
                }
            }
        }
    }
}

