package ai.masaic.platform.api.repository

import ai.masaic.platform.api.model.UserLoginAudit
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository interface for managing user login audit records.
 */
interface UserLoginAuditRepository {
    /**
     * Save a user login audit record.
     *
     * @param audit The audit record to save
     * @return The saved audit record
     */
    suspend fun save(audit: UserLoginAudit): UserLoginAudit

    /**
     * Find all login audit records for a specific user.
     *
     * @param userId The user ID to search for
     * @return List of audit records for the user
     */
    suspend fun findByUserId(userId: String): List<UserLoginAudit>

    /**
     * Find all login audit records within a time range.
     *
     * @param startTime The start time
     * @param endTime The end time
     * @return List of audit records within the time range
     */
    suspend fun findByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): List<UserLoginAudit>
}

/**
 * MongoDB implementation of UserLoginAuditRepository.
 */
class MongoUserLoginAuditRepository(
    private val mongoTemplate: ReactiveMongoTemplate,
) : UserLoginAuditRepository {
    companion object {
        private const val COLLECTION_NAME = "user_login_audit"
    }

    override suspend fun save(audit: UserLoginAudit): UserLoginAudit = mongoTemplate.save(audit, COLLECTION_NAME).awaitSingle()

    override suspend fun findByUserId(userId: String): List<UserLoginAudit> {
        val query =
            Query
                .query(Criteria.where("userId").`is`(userId))
                .with(Sort.by(Sort.Direction.DESC, "loginTime"))
        return mongoTemplate.find(query, UserLoginAudit::class.java, COLLECTION_NAME).collectList().awaitSingle()
    }

    override suspend fun findByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): List<UserLoginAudit> {
        val query =
            Query
                .query(
                    Criteria
                        .where("loginTime")
                        .gte(startTime)
                        .lte(endTime),
                ).with(Sort.by(Sort.Direction.DESC, "loginTime"))
        return mongoTemplate.find(query, UserLoginAudit::class.java, COLLECTION_NAME).collectList().awaitSingle()
    }
}

/**
 * In-memory implementation of UserLoginAuditRepository.
 */
class InMemoryUserLoginAuditRepository : UserLoginAuditRepository {
    private val auditStore = ConcurrentHashMap<String, UserLoginAudit>()

    override suspend fun save(audit: UserLoginAudit): UserLoginAudit {
        val auditWithId = audit.copy(id = audit.id ?: UUID.randomUUID().toString())
        auditStore[auditWithId.id!!] = auditWithId
        return auditWithId
    }

    override suspend fun findByUserId(userId: String): List<UserLoginAudit> =
        auditStore.values
            .filter { it.userId == userId }
            .sortedByDescending { it.loginTime }

    override suspend fun findByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): List<UserLoginAudit> =
        auditStore.values
            .filter { it.loginTime >= startTime && it.loginTime <= endTime }
            .sortedByDescending { it.loginTime }
}

