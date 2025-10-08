package ai.masaic.platform.api.user

data class UserInfo(
    val userId: String,
)

interface UserInfoProvider {
    suspend fun userId(): String? = null

    suspend fun sessionId(): String? = null

    companion object {
        private var infoProvider: UserInfoProvider = NoOpUserInfoProvider()

        fun init(infoProvider: UserInfoProvider) {
            UserInfoProvider.infoProvider = infoProvider
        }

        suspend fun userId(): String? = infoProvider.userId()

        suspend fun sessionId(): String? = infoProvider.sessionId()
    }
}

class NoOpUserInfoProvider : UserInfoProvider
