package ai.masaic.platform.api.controller

import ai.masaic.platform.api.security.auth.google.GoogleTokenVerifier
import ai.masaic.platform.api.service.UserLoginAuditService
import ai.masaic.platform.api.user.UserInfo
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v1/dashboard/platform/auth")
@CrossOrigin("*")
@ConditionalOnProperty(name = ["platform.deployment.auth.enabled"], havingValue = "true")
class AuthController(
    private val googleTokenVerifier: GoogleTokenVerifier,
    private val userLoginAuditService: UserLoginAuditService,
) {
    @PostMapping("/verify")
    suspend fun verifyToken(
        @RequestBody request: TokenVerificationRequest,
    ): ResponseEntity<UserInfo> {
        val userInfo =
            when (request.authProvider) {
                "Google" -> googleTokenVerifier.verifyAsync(request.token).awaitSingle()
                else -> throw IllegalArgumentException("Auth provider ${request.authProvider} is not supported")
            }

        // Log user login audit
        try {
            userLoginAuditService.logUserLogin(userInfo, request.authProvider)
        } catch (e: Exception) {
            log.error(e) { "Failed to log user login audit for userId=${userInfo.userId}" }
            // Continue processing even if audit logging fails
        }

        return ResponseEntity.ok(userInfo)
    }
}

data class TokenVerificationRequest(
    val token: String,
    val authProvider: String = "Google",
)
