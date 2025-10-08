package ai.masaic.platform.api.controller

import ai.masaic.platform.api.service.AgentRunException
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["ai.masaic.platform.api.controller"])
class PlatformExceptionHandler {
    private val logger = KotlinLogging.logger {}

    private fun logError(
        status: HttpStatus,
        ex: Throwable,
        message: String,
    ) {
        if (status.is5xxServerError) {
            logger.error(ex) { message }
        } else {
            logger.error { message }
        }
    }

    @ExceptionHandler(exception = [AgentRunException::class])
    fun handleResponseProcessingException(ex: Exception): ResponseEntity<ErrorResponse> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        logError(status, ex, "Error processing response: ${ex.message}")

        val errorResponse =
            ErrorResponse(
                type = "processing_error",
                message = ex.message ?: "Error processing response",
                param = null,
                code = status.value().toString(),
                timestamp = System.currentTimeMillis(),
            )
        return ResponseEntity.status(status).body(errorResponse)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorResponse(
    val type: String? = null,
    val message: String? = null,
    val param: String? = null,
    val code: String? = null,
    val timestamp: Long? = null,
)
