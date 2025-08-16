package ai.masaic.platform.api.registry.functions

import com.mongodb.client.result.DeleteResult
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository interface for function registry operations.
 */
interface FunctionRegistryRepository {
    suspend fun save(function: FunctionDoc): FunctionDoc
    suspend fun findByName(name: String): FunctionDoc?
    suspend fun findAll(limit: Int = 100, cursor: String? = null): List<FunctionDoc>
    suspend fun searchByName(query: String, limit: Int = 100): List<FunctionDoc>
    suspend fun updateByName(name: String, update: FunctionUpdate): FunctionDoc?
    suspend fun deleteByName(name: String): Boolean
    suspend fun existsByName(name: String): Boolean
}

/**
 * MongoDB implementation of FunctionRegistryRepository.
 */
@Repository
@ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "mongodb")
class MongoFunctionRegistryRepository(
    private val mongoTemplate: ReactiveMongoTemplate
) : FunctionRegistryRepository {

    companion object {
        private const val COLLECTION_NAME = "function_registry"
    }

    override suspend fun save(function: FunctionDoc): FunctionDoc {
        return mongoTemplate.save(function, COLLECTION_NAME).awaitSingle()
    }

    override suspend fun findByName(name: String): FunctionDoc? {
        return mongoTemplate.findById(name, FunctionDoc::class.java, COLLECTION_NAME).awaitSingleOrNull()
    }

    override suspend fun findAll(limit: Int, cursor: String?): List<FunctionDoc> {
        val query = Query().with(Sort.by(Sort.Direction.DESC, "updatedAt")).limit(limit)
        
        // TODO: Implement cursor-based pagination when needed
        cursor?.let {
            // For now, just return all results up to limit
            // In future: query.where("_id").gt(cursor)
        }
        
        return mongoTemplate.find(query, FunctionDoc::class.java, COLLECTION_NAME)
            .collectList()
            .awaitSingle()
    }

    override suspend fun searchByName(query: String, limit: Int): List<FunctionDoc> {
        val criteria = Criteria.where("name").regex(query, "i")
        val mongoQuery = Query(criteria).with(Sort.by(Sort.Direction.DESC, "updatedAt")).limit(limit)
        
        return mongoTemplate.find(mongoQuery, FunctionDoc::class.java, COLLECTION_NAME)
            .collectList()
            .awaitSingle()
    }

    override suspend fun updateByName(name: String, update: FunctionUpdate): FunctionDoc? {
        val existing = findByName(name) ?: return null
        
        val updateDoc = Update()
        update.description?.let { updateDoc.set("description", it) }
        update.deps?.let { updateDoc.set("deps", it) }
        update.code?.let { updateDoc.set("code", it) }
        updateDoc.set("updatedAt", Instant.now())
        
        val query = Query(Criteria.where("_id").`is`(name))
        val result = mongoTemplate.updateFirst(query, updateDoc, COLLECTION_NAME).awaitSingle()
        
        return if (result.modifiedCount > 0) {
            findByName(name)
        } else {
            null
        }
    }

    override suspend fun deleteByName(name: String): Boolean {
        val query = Query(Criteria.where("_id").`is`(name))
        val result: DeleteResult = mongoTemplate.remove(query, COLLECTION_NAME).awaitSingle()
        return result.deletedCount > 0
    }

    override suspend fun existsByName(name: String): Boolean {
        val query = Query(Criteria.where("_id").`is`(name))
        return mongoTemplate.exists(query, FunctionDoc::class.java, COLLECTION_NAME).awaitSingle()
    }
}
