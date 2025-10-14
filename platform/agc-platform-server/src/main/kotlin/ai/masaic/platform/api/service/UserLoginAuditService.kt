package ai.masaic.platform.api.service

import ai.masaic.platform.api.model.UserLoginAudit
import ai.masaic.platform.api.repository.UserLoginAuditRepository
import ai.masaic.platform.api.user.UserInfo
import mu.KotlinLogging
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * Service for managing user login audit records.
 */
class UserLoginAuditService(
    private val userLoginAuditRepository: UserLoginAuditRepository,
) {
    /**
     * Log a user login event.
     *
     * @param userInfo The user information from authentication
     * @param authProvider The authentication provider (e.g., "Google")
     */
    suspend fun logUserLogin(
        userInfo: UserInfo,
        authProvider: String = "Google",
    ) {
        try {
            val audit =
                UserLoginAudit(
                    userId = userInfo.userId,
                    firstName = userInfo.firstName,
                    fullName = userInfo.fullName,
                    loginTime = Instant.now(),
                    authProvider = authProvider,
                )

            val savedAudit = userLoginAuditRepository.save(audit)
            log.info {
                "User login audit logged: userId=${savedAudit.userId}, " +
                    "fullName=${savedAudit.fullName}, " +
                    "loginTime=${savedAudit.loginTime}, " +
                    "authProvider=${savedAudit.authProvider}"
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to save user login audit for userId=${userInfo.userId}" }
        }
    }

    /**
     * Get login history for a specific user.
     *
     * @param userId The user ID
     * @return List of login audit records for the user
     */
    suspend fun getUserLoginHistory(userId: String): List<UserLoginAudit> = userLoginAuditRepository.findByUserId(userId)

    /**
     * Get login audit records within a time range.
     *
     * @param startTime The start time
     * @param endTime The end time
     * @return List of login audit records within the time range
     */
    suspend fun getLoginAuditsByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): List<UserLoginAudit> = userLoginAuditRepository.findByTimeRange(startTime, endTime)
}
