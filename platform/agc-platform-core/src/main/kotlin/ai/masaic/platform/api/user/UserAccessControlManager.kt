package ai.masaic.platform.api.user

import ai.masaic.openresponses.api.user.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class UserAccessControlManager : AllowAllAccessManager() {
    private val mapper = jacksonObjectMapper()

    override suspend fun computeAccessControl() = UserInfoProvider.userId()?.let { UserAccessControl(userId = it) } ?: NoAccessControl()

    override suspend fun isAccessPermitted(assignedControl: AccessControl?): Boolean {
        val userInfo = UserInfoProvider.userInfo()
        val canAccess =
            when {
                assignedControl?.grantedScope == Scope.FULL -> true
                userInfo == null -> false
                assignedControl == null -> userInfo.grantedScope == Scope.FULL
                userInfo.grantedScope == Scope.FULL -> true
                assignedControl is UserAccessControl -> userInfo.userId == assignedControl.userId
                assignedControl is NoAccessControl -> true
                else -> false
            }
        return canAccess
    }

    override suspend fun toString(accessControl: AccessControl): String =
        when (accessControl) {
            is UserAccessControl -> mapper.writeValueAsString(mapOf("userInfo" to accessControl.userId, "grantedScope" to accessControl.grantedScope.name))
            is NoAccessControl -> super.toString(accessControl)
            else -> throw IllegalStateException("unknown access control type.")
        }

    override suspend fun fromString(accessControl: String): AccessControl =
        try {
            val jsonNode = mapper.readTree(accessControl)
            if (jsonNode.has("userInfo")) UserAccessControl(userId = jsonNode["userInfo"].asText(), grantedScope = Scope.valueOf(jsonNode["grantedScope"].asText())) else super.fromString(accessControl)
        } catch (ex: Exception) {
            super.fromString(accessControl)
        }
}

data class UserAccessControl(
    override val grantedScope: Scope = Scope.RESTRICTED,
    val userId: String,
) : AccessControl(grantedScope = grantedScope)
