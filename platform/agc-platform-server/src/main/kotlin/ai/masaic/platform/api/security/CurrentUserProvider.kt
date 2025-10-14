package ai.masaic.platform.api.security

import ai.masaic.openresponses.api.user.Scope
import ai.masaic.platform.api.user.UserInfo
import ai.masaic.platform.api.user.UserInfoProvider
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

class CurrentUserProvider : UserInfoProvider {
    companion object {
        private const val USER_ID_KEY = "x-user-id"
        private const val SESSION_ID_KEY = "x-session-id"
    }

    override suspend fun userId(): String? {
        // 1) Prefer SecurityContext (auth-enabled, source of truth)
        val fromPrincipal = getPrincipalUserId()
        if (!fromPrincipal.isNullOrBlank()) return fromPrincipal
        // 2) Fallback to Reactor Context (auth-disabled path)
        return getFromContext(USER_ID_KEY)
    }

    override suspend fun sessionId(): String? = getFromContext(SESSION_ID_KEY)

    override suspend fun userInfo(): UserInfo? {
        val fromPrincipal = getPrincipalUserInfo()
        return if (fromPrincipal != null) {
            fromPrincipal
        } else {
            val userId = getFromContext(USER_ID_KEY)
            userId?.let { UserInfo(userId = userId, grantedScope = Scope.FULL) }
        }
    }

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

    private suspend fun getPrincipalUserInfo(): UserInfo? =
        ReactiveSecurityContextHolder
            .getContext()
            .mapNotNull { it.authentication?.principal }
            .mapNotNull { principal ->
                when (principal) {
                    is UserInfo -> principal
                    else -> null
                }
            }.awaitSingleOrNull()
}
