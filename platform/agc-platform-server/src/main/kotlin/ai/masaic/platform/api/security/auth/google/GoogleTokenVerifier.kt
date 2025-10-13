package ai.masaic.platform.api.security.auth.google

import ai.masaic.platform.api.security.GoogleAuthConfig
import ai.masaic.platform.api.security.PlatformAccessForbiddenException
import ai.masaic.platform.api.user.Scope
import ai.masaic.platform.api.user.UserInfo
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import mu.KotlinLogging
import org.springframework.security.authentication.BadCredentialsException
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Verifies Google ID tokens using Google's official SDK
 */
class GoogleTokenVerifier(
    private val googleAuthConfig: GoogleAuthConfig,
    private val whitelistedUsers: Set<String>? = null,
    private val adminUsers: Set<String>? = null,
) {
    private val log = KotlinLogging.logger { }
    private val verifier =
        GoogleIdTokenVerifier
            .Builder(NetHttpTransport(), GsonFactory())
            .setAudience(Collections.singletonList(googleAuthConfig.audience))
            .setIssuer(googleAuthConfig.issuer)
            .build()

    fun verifyAsync(token: String): Mono<UserInfo> =
        Mono
            .fromFuture(
                CompletableFuture.supplyAsync {
                    val idToken =
                        verifier.verify(token)
                            ?: throw BadCredentialsException("Invalid Google token")

                    val payload = idToken.payload
                    var userInfo =
                        UserInfo(
                            userId = payload.email,
                            fullName = if (payload["name"] is String) payload["name"] as String else "User",
                            firstName = if (payload["given_name"] is String) payload["given_name"] as String else "User",
                        )

                    whitelistedUsers?.let {
                        if (!(whitelistedUsers.isNotEmpty() && whitelistedUsers.contains(userInfo.userId))) {
                            val error = "$userInfo is not whitelisted for platform access. Contact admin for access."
                            log.error { error }
                            throw PlatformAccessForbiddenException(error)
                        }
                    }

                    adminUsers?.let {
                        if (adminUsers.isNotEmpty() && adminUsers.contains(userInfo.userId)) {
                            userInfo = userInfo.copy(grantedScope = Scope.FULL)
                        }
                    }
                    userInfo
                },
            ).onErrorMap { BadCredentialsException("Invalid Google token", it) }
}
