package ai.masaic.platform.api.user

import ai.masaic.openresponses.api.user.Scope
import ai.masaic.openresponses.api.utils.AgCLoopContext
import ai.masaic.openresponses.api.utils.LoopContextInfo

data class UserInfo(
    val userId: String,
    val fullName: String = "User",
    val firstName: String = "User",
    val loggedIn: Boolean = false,
    val grantedScope: Scope = Scope.RESTRICTED,
)

const val SYSTEM_USER = "agc_system_user"

interface UserInfoProvider {
    suspend fun userId(): String? = null

    suspend fun sessionId(): String? = null

    suspend fun userInfo(): UserInfo? = null

    companion object {
        private var infoProvider: UserInfoProvider = NoOpUserInfoProvider()

        fun init(infoProvider: UserInfoProvider) {
            UserInfoProvider.infoProvider = infoProvider
        }

        suspend fun userId(): String? = infoProvider.userId() ?: AgCLoopContext.userId()

        suspend fun userInfo(): UserInfo? =
            infoProvider.userInfo() ?: AgCLoopContext.userId()?.let {
                val userId = AgCLoopContext.userId() ?: return@let null
                if (SYSTEM_USER == userId) {
                    UserInfo(userId = userId, grantedScope = Scope.FULL, fullName = userId, firstName = userId)
                } else {
                    UserInfo(userId = AgCLoopContext.userId() ?: "")
                }
            }

        suspend fun sessionId(): String? = infoProvider.sessionId() ?: AgCLoopContext.sessionId()

        suspend fun toLoopContextInfo(loopId: String?) = LoopContextInfo(userId(), sessionId(), loopId)
    }
}

class NoOpUserInfoProvider : UserInfoProvider
