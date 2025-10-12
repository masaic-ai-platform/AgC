package ai.masaic.platform.api.controller

import ai.masaic.platform.api.security.auth.google.GoogleTokenVerifier
import ai.masaic.platform.api.user.UserInfo
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/dashboard/platform/auth")
@CrossOrigin("*")
@ConditionalOnProperty(name = ["platform.deployment.auth.enabled"], havingValue = "true")
class AuthController(
    private val googleTokenVerifier: GoogleTokenVerifier,
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
        return ResponseEntity.ok(userInfo)
    }
}

data class TokenVerificationRequest(
    val token: String,
    val authProvider: String = "Google",
)
