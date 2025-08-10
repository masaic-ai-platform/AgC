package ai.masaic.openresponses.api.user

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Provider for accessing current user information in coroutines
 */
interface CurrentUserProvider {
    suspend fun getUser(): UserInfo?

    suspend fun getCurrentUserId(): String? = getUser()?.userId
}

@Profile("!platform")
@Component
class NoOpCurrentUserProvider : CurrentUserProvider {
    override suspend fun getUser(): UserInfo? = null
}

data class UserInfo(
    val userId: String,
)
