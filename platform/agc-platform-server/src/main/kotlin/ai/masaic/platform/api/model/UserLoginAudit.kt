package ai.masaic.platform.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * User login audit entity to track user authentication events.
 */
@Document(collection = "user_login_audit")
data class UserLoginAudit(
    @Id
    val id: String? = null,
    val userId: String,
    val firstName: String,
    val fullName: String,
    val loginTime: Instant = Instant.now(),
    val authProvider: String = "Google",
)
