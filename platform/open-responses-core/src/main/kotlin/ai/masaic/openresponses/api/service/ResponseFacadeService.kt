package ai.masaic.openresponses.api.service

import ai.masaic.openresponses.api.model.CreateResponseRequest
import ai.masaic.openresponses.api.utils.AgCLoopContext
import ai.masaic.openresponses.api.utils.PayloadFormatter
import ai.masaic.openresponses.api.validation.RequestValidator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap

private val logger = KotlinLogging.logger {}

/**
 * Facade service that provides a unified interface for response processing.
 * This service handles validation, formatting, and routing for both streaming and non-streaming responses.
 * 
 * It acts as an intermediary between the ResponseController and MasaicResponseService,
 * encapsulating the common logic for request preprocessing and response routing.
 */
@Service
class ResponseFacadeService(
    private val masaicResponseService: MasaicResponseService,
    private val requestValidator: RequestValidator,
    private val payloadFormatter: PayloadFormatter,
) {
    private val mapper = jacksonObjectMapper()

    /**
     * Processes a response request by performing validation, formatting, and routing
     * to the appropriate service method based on streaming requirements.
     *
     * @param request The original response request
     * @param headers HTTP headers for the request
     * @param queryParams Query parameters for the request
     * @return ResponseProcessingResult containing either streaming or non-streaming response
     */
    private suspend fun processResponse(
        request: CreateResponseRequest,
        headers: MultiValueMap<String, String>,
        queryParams: MultiValueMap<String, String>,
    ): ResponseProcessingResult {
        logger.info { "Processing response request for model: ${request.model}, streaming: ${request.stream}" }

        // Step 1: Validate the request
        validateRequest(request)

        // Step 2: Format the request (update tools, etc.)
        formatRequest(request)

        // Step 3: Parse input and prepare request body
        val preparedRequest = prepareRequest(request)

        // Step 4: Route to appropriate service method based on streaming requirement
        return if (request.stream) {
            logger.debug { "Routing to streaming response service" }
            ResponseProcessingResult.Streaming(
                masaicResponseService.createStreamingResponse(
                    preparedRequest,
                    headers,
                    queryParams,
                ),
            )
        } else {
            logger.debug { "Routing to non-streaming response service" }
            val response =
                masaicResponseService.createResponse(
                    preparedRequest,
                    headers,
                    queryParams,
                )
            ResponseProcessingResult.NonStreaming(response)
        }
    }

    suspend fun agCLoopWithRequestContext(agCLoopRequest: AgCLoopRequest): ResponseProcessingResult {
        val ctx = AgCLoopContext(userId = agCLoopRequest.userId, sessionId = agCLoopRequest.sessionId, loopId = agCLoopRequest.id)
        val result =
            withContext(ctx) {
                processResponse(
                    request = agCLoopRequest.request,
                    headers = mapOf("Authorization" to "Bearer ${agCLoopRequest.apiKey}").toMultiValueMap(),
                    queryParams = emptyMap<String, String>().toMultiValueMap(),
                )
            }

        return when (result) {
            is ResponseProcessingResult.Streaming -> ResponseProcessingResult.Streaming(result.flow.flowOn(ctx))
            is ResponseProcessingResult.NonStreaming -> result
        }
    }

    /**
     * Validates the incoming response request using the configured validator.
     *
     * @param request The request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private suspend fun validateRequest(request: CreateResponseRequest) {
        logger.debug { "Validating response request" }
        try {
            requestValidator.validateResponseRequest(request)
            logger.debug { "Request validation completed successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Request validation failed: ${e.message}" }
            throw e
        }
    }

    /**
     * Formats the request by updating tools and other payload modifications.
     *
     * @param request The request to format (modified in place)
     */
    private suspend fun formatRequest(request: CreateResponseRequest) {
        logger.debug { "Formatting response request" }
        try {
            payloadFormatter.formatResponseRequest(request)
            logger.debug { "Request formatting completed successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Request formatting failed: ${e.message}" }
            throw e
        }
    }

    /**
     * Prepares the request by parsing input and converting to the required format.
     *
     * @param request The request to prepare
     * @return ResponseCreateParams.Body ready for the service call
     */
    private suspend fun prepareRequest(request: CreateResponseRequest): ResponseCreateParams.Body {
        logger.debug { "Preparing request body" }
        try {
            // Parse input as required by the original controller
            request.parseInput(mapper)
            
            // Convert to JSON and back to ResponseCreateParams.Body format
            val requestBodyJson = mapper.writeValueAsString(request)
            logger.debug { "Prepared request body: $requestBodyJson" }
            
            return mapper.readValue(
                requestBodyJson,
                ResponseCreateParams.Body::class.java,
            )
        } catch (e: Exception) {
            logger.error(e) { "Request preparation failed: ${e.message}" }
            throw ResponseProcessingException("Failed to prepare request: ${e.message}")
        }
    }

    /**
     * Simplified method for processing responses with a simple input object.
     * This method is designed for easy reuse throughout the codebase without dealing with Spring's MultiValueMap.
     *
     * @param input Simple input object containing request and context
     * @return ResponseProcessingResult containing either streaming or non-streaming response
     */
    suspend fun processResponse(input: ResponseProcessingInput): ResponseProcessingResult {
        logger.info { "Processing response request (simple input) for model: ${input.request.model}, streaming: ${input.request.stream}" }
        
        return processResponse(
            request = input.request,
            headers = input.headers.toMultiValueMap(),
            queryParams = input.queryParams.toMultiValueMap(),
        )
    }

    suspend fun processResponseForController(
        request: CreateResponseRequest,
        headers: MultiValueMap<String, String>,
        queryParams: MultiValueMap<String, String>,
    ): Any {
        val response = processResponse(request, headers, queryParams)
        return when (response) {
            is ResponseProcessingResult.NonStreaming -> payloadFormatter.formatResponse(response.response)
            is ResponseProcessingResult.Streaming -> response.flow
        }
    }

    /**
     * Extension function to convert a regular Map to Spring's MultiValueMap
     */
    private fun Map<String, String>.toMultiValueMap(): MultiValueMap<String, String> {
        val multiValueMap = org.springframework.util.LinkedMultiValueMap<String, String>()
        this.forEach { (key, value) ->
            multiValueMap.add(key, value)
        }
        return multiValueMap
    }
}

/**
 * Simple input object for easier reuse of the facade service throughout the codebase.
 * This avoids the complexity of Spring's MultiValueMap when calling from other services.
 *
 * @property request The response request object
 * @property headers HTTP headers as a simple map (defaults to empty)
 * @property queryParams Query parameters as a simple map (defaults to empty)
 */
data class ResponseProcessingInput(
    val request: CreateResponseRequest,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
)

data class AgCLoopRequest(
    val id: String,
    val request: CreateResponseRequest,
    val apiKey: String,
    val sessionId: String? = null,
    val userId: String? = null,
)

/**
 * Sealed class representing the result of response processing.
 * This allows the controller to handle streaming and non-streaming responses appropriately.
 */
sealed class ResponseProcessingResult {
    /**
     * Result for streaming responses containing a flow of server-sent events.
     */
    data class Streaming(
        val flow: Flow<ServerSentEvent<String>>,
    ) : ResponseProcessingResult()

    /**
     * Result for non-streaming responses containing the formatted response object.
     */
    data class NonStreaming(
        val response: Response,
    ) : ResponseProcessingResult()
}
