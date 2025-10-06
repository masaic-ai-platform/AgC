package ai.masaic.platform.api.user

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

data class UserInfo(
    val userId: String,
)

object CurrentUserProvider {
    private const val USER_ID_KEY = "x-user-id"
    private const val SESSION_ID_KEY = "x-session-id"

    suspend fun userId(): String? {
        // 1) Prefer SecurityContext (auth-enabled, source of truth)
        val fromPrincipal = getPrincipalUserId()
        if (!fromPrincipal.isNullOrBlank()) return fromPrincipal
        // 2) Fallback to Reactor Context (auth-disabled path)
        return getFromContext(USER_ID_KEY)
    }

    suspend fun sessionId(): String? = getFromContext(SESSION_ID_KEY)

    suspend fun hasUserId(): Boolean = !userId().isNullOrBlank()

    suspend fun hasSessionId(): Boolean = !sessionId().isNullOrBlank()

    suspend fun userInfo(): UserInfo? = userId()?.let { UserInfo(it) }

    private suspend fun getFromContext(key: String): String? =
        Mono
            .deferContextual { ctx ->
                val value = if (ctx.hasKey(key)) ctx.get<String>(key) else null
                Mono.justOrEmpty(value)
            }.awaitSingleOrNull()

    private suspend fun getPrincipalUserId(): String? =
        ReactiveSecurityContextHolder
            .getContext()
            .mapNotNull { it.authentication?.principal }
            .mapNotNull { principal ->
                when (principal) {
                    is UserInfo -> principal.userId
                    is org.springframework.security.core.userdetails.UserDetails -> principal.username
                    else -> null
                }
            }.awaitSingleOrNull()
}
