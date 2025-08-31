package ai.masaic.platform.api.repository

import ai.masaic.platform.api.model.PlatformAgentMeta
import com.mongodb.client.result.DeleteResult
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant

interface AgentRepository {
    suspend fun upsert(agentMeta: PlatformAgentMeta): PlatformAgentMeta

    suspend fun findByName(name: String): PlatformAgentMeta?

    suspend fun deleteByName(name: String): Boolean

    suspend fun findAll(): List<PlatformAgentMeta>
}

class MongoAgentRepository(
    private val mongoTemplate: ReactiveMongoTemplate,
) : AgentRepository {
    companion object {
        private const val COLLECTION_NAME = "agents"
    }

    override suspend fun upsert(agentMeta: PlatformAgentMeta): PlatformAgentMeta {
        val agentWithTimestamp =
            agentMeta.copy(
                createdAt = agentMeta.createdAt ?: Instant.now(),
                updatedAt = Instant.now(),
            )
        return mongoTemplate.save(agentWithTimestamp, COLLECTION_NAME).awaitSingle()
    }

    override suspend fun findByName(name: String): PlatformAgentMeta? {
        // Since name is now the ID, we can query by _id directly
        val query = Query.query(Criteria.where("_id").`is`(name))
        return mongoTemplate.findOne(query, PlatformAgentMeta::class.java, COLLECTION_NAME).awaitSingleOrNull()
    }

    override suspend fun deleteByName(name: String): Boolean {
        // Since name is now the ID, we can delete by _id directly
        val query = Query.query(Criteria.where("_id").`is`(name))
        val result: DeleteResult = mongoTemplate.remove(query, COLLECTION_NAME).awaitSingle()
        return result.deletedCount > 0
    }

    override suspend fun findAll(): List<PlatformAgentMeta> {
        val query = Query().with(Sort.by(Sort.Direction.DESC, "updatedAt"))
        return mongoTemplate.find(query, PlatformAgentMeta::class.java, COLLECTION_NAME).collectList().awaitSingle()
    }
}

class InMemoryAgentRepository : AgentRepository {
    private val agents = mutableMapOf<String, PlatformAgentMeta>()

    override suspend fun upsert(agentMeta: PlatformAgentMeta): PlatformAgentMeta {
        val agentWithTimestamp =
            agentMeta.copy(
                createdAt = agentMeta.createdAt ?: Instant.now(),
                updatedAt = Instant.now(),
            )
        agents[agentMeta.name] = agentWithTimestamp
        return agentWithTimestamp
    }

    override suspend fun findByName(name: String): PlatformAgentMeta? {
        return agents[name]
    }

    override suspend fun deleteByName(name: String): Boolean {
        val existed = agents.containsKey(name)
        agents.remove(name)
        return existed
    }

    override suspend fun findAll(): List<PlatformAgentMeta> = agents.values.sortedByDescending { it.updatedAt }

    fun clear() {
        agents.clear()
    }

    fun size(): Int = agents.size
}
