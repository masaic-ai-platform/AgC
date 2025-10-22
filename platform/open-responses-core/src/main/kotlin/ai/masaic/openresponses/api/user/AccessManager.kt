package ai.masaic.openresponses.api.user

interface AccessManager {
    suspend fun computeAccessControl(): AccessControl = NoAccessControl()

    suspend fun isAccessPermitted(assignedControl: AccessControl?): Rights = Rights()

    suspend fun toString(accessControl: AccessControl): String

    suspend fun fromString(accessControl: String): AccessControl

    companion object {
        private var accessManager: AccessManager = AllowAllAccessManager()

        fun initialise(accessManager: AccessManager) {
            AccessManager.accessManager = accessManager
        }

        suspend fun computeAccessControl() = accessManager.computeAccessControl()

        suspend fun isAccessPermitted(assignedControl: AccessControl?) = accessManager.isAccessPermitted(assignedControl)

        suspend fun toString(accessControl: AccessControl) = accessManager.toString(accessControl)

        suspend fun fromString(accessControl: String) = accessManager.fromString(accessControl)

        suspend fun isAccessPermitted(assignedControlStr: String?): Rights {
            val accessControl = assignedControlStr?.let { fromString(assignedControlStr) }
            return isAccessPermitted(accessControl)
        }
    }
}

open class AllowAllAccessManager : AccessManager {
    override suspend fun toString(accessControl: AccessControl): String = accessControl.grantedScope.name

    override suspend fun fromString(accessControl: String): AccessControl = NoAccessControl(Scope.valueOf(accessControl))
}

open class AccessControl(
    open val grantedScope: Scope,
)

data class NoAccessControl(
    override val grantedScope: Scope = Scope.FULL,
) : AccessControl(grantedScope = grantedScope)

enum class Scope {
    RESTRICTED,
    FULL,
}

data class Rights(
    val read: Boolean = true,
    val write: Boolean = true,
    val update: Boolean = true,
    val delete: Boolean = true,
)
