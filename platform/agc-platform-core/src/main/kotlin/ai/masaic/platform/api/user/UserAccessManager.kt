package ai.masaic.platform.api.user

class UserAccessManager {
    companion object {
        suspend fun computeAccessControl() =
            UserInfoProvider.userId()?.let { UserAccessControl(userId = it) } ?: NoAccessControl()

        suspend fun isAccessPermitted(assignedControl: AccessControl): Boolean {
            return if (assignedControl.grantedScope == Scope.FULL) true
            else {
                val userInfo = UserInfoProvider.userInfo()
                userInfo?.let {
                    when (assignedControl) {
                        is UserAccessControl -> userInfo.userId == assignedControl.userId
                        is NoAccessControl -> true
                        else -> false
                    }
                } ?: false
            }
        }
    }
}

open class AccessControl(
    open val grantedScope: Scope
)


data class UserAccessControl(
    override val grantedScope: Scope = Scope.RESTRICTED,
    val userId: String,
): AccessControl(grantedScope = grantedScope)

data class NoAccessControl(
    override val grantedScope: Scope = Scope.FULL,
): AccessControl(grantedScope = grantedScope)
