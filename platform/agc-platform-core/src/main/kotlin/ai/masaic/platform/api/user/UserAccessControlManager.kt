package ai.masaic.platform.api.user

import ai.masaic.openresponses.api.user.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class UserAccessControlManager : AllowAllAccessManager() {
    private val mapper = jacksonObjectMapper()

    override suspend fun computeAccessControl() =
        UserInfoProvider.userInfo()?.let {
            if (it.userId == SYSTEM_USER) {
                UserAccessControl(
                    userId = it.userId,
                    grantedScope = Scope.FULL,
                    deletionScope = Scope.RESTRICTED,
                )
            } else {
                if (it.loggedIn) {
                    UserAccessControl(userId = it.userId)
                } else {
                    NoAccessControl()
                }
            }
        } ?: NoAccessControl()

    override suspend fun isAccessPermitted(assignedControl: AccessControl?): Rights {
        val userInfo = UserInfoProvider.userInfo()
        val canAccess =
            when {
                userInfo == null -> Rights()
                assignedControl is UserAccessControl -> {
                    if (assignedControl.userId == SYSTEM_USER) {
                        Rights(read = true, write = true, update = userInfo.grantedScope == Scope.FULL, delete = userInfo.grantedScope == Scope.FULL)
                    } else if (!userInfo.loggedIn) {
                        Rights()
                    } else {
                        rights(userInfo.userId, assignedControl)
                    }
                }
                assignedControl is NoAccessControl -> Rights()
                assignedControl?.grantedScope == Scope.FULL -> Rights()
                !userInfo.loggedIn && userInfo.grantedScope == Scope.FULL -> Rights()
                assignedControl == null -> rights(userInfo)
                else -> Rights(read = false, write = false, update = false, delete = false)
            }
        return canAccess
    }

    private fun rights(
        userId: String,
        userAccessControl: UserAccessControl,
    ) = Rights(read = userId == userAccessControl.userId, write = userId == userAccessControl.userId, update = userId == userAccessControl.userId, delete = userId == userAccessControl.userId)

    private fun rights(userInfo: UserInfo) = Rights(read = userInfo.grantedScope == Scope.FULL, write = userInfo.grantedScope == Scope.FULL, update = userInfo.grantedScope == Scope.FULL, delete = userInfo.grantedScope == Scope.FULL)

    override suspend fun toString(accessControl: AccessControl): String =
        when (accessControl) {
            is UserAccessControl -> mapper.writeValueAsString(mapOf("userInfo" to accessControl.userId, "grantedScope" to accessControl.grantedScope.name, "deletionScope" to accessControl.deletionScope.name))
            is NoAccessControl -> super.toString(accessControl)
            else -> throw IllegalStateException("unknown access control type.")
        }

    override suspend fun fromString(accessControl: String): AccessControl =
        try {
            val jsonNode = mapper.readTree(accessControl)
            if (jsonNode.has("userInfo")) {
                val deletionScope = if (jsonNode.has("deletionScope")) Scope.valueOf(jsonNode["deletionScope"].asText()) else Scope.RESTRICTED
                UserAccessControl(userId = jsonNode["userInfo"].asText(), grantedScope = Scope.valueOf(jsonNode["grantedScope"].asText()), deletionScope = deletionScope)
            } else {
                super.fromString(accessControl)
            }
        } catch (ex: Exception) {
            super.fromString(accessControl)
        }
}

data class UserAccessControl(
    override val grantedScope: Scope = Scope.RESTRICTED,
    val deletionScope: Scope = Scope.RESTRICTED,
    val userId: String,
) : AccessControl(grantedScope = grantedScope)
