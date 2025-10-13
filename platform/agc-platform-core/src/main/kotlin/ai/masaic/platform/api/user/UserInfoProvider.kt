package ai.masaic.platform.api.user

import ai.masaic.openresponses.api.utils.AgCLoopContext
import ai.masaic.openresponses.api.utils.AgCLoopContext.Key.loopId
import ai.masaic.openresponses.api.utils.LoopContextInfo

data class UserInfo(
    val userId: String,
    val fullName: String = "User",
    val firstName: String = "User",
    val scope: Scope = Scope.FULL,
)

enum class Scope {
    RESTRICTED,
    FULL
}

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
            infoProvider.userInfo() ?: AgCLoopContext.userId()?.let { UserInfo(userId = AgCLoopContext.userId() ?: "")}

        suspend fun sessionId(): String? = infoProvider.sessionId() ?: AgCLoopContext.sessionId()

        suspend fun toLoopContextInfo(loopId: String?) = LoopContextInfo(userId(), sessionId(), loopId)
    }
}

class NoOpUserInfoProvider : UserInfoProvider
