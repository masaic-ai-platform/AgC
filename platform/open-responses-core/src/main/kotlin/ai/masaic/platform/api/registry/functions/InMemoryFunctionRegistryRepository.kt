package ai.masaic.platform.api.registry.functions

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * In-memory implementation of FunctionRegistryRepository using Caffeine cache.
 * Stores data in a cache with LRU eviction and a maximum size of 1000.
 * Only enabled when open-responses.store.type=in-memory
 */
@Repository
@ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "in-memory", matchIfMissing = true)
class InMemoryFunctionRegistryRepository : FunctionRegistryRepository {
    
    private val cache: Cache<String, FunctionDoc> = Caffeine.newBuilder()
        .maximumSize(1000)
        .build()

    override suspend fun save(function: FunctionDoc): FunctionDoc {
        cache.put(function.name, function)
        return function
    }

    override suspend fun findByName(name: String): FunctionDoc? {
        return cache.getIfPresent(name)
    }

    override suspend fun findAll(limit: Int, cursor: String?): List<FunctionDoc> {
        return cache.asMap().values
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    override suspend fun searchByName(query: String, limit: Int): List<FunctionDoc> {
        return cache.asMap().values
            .filter { it.name.contains(query, ignoreCase = true) }
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    override suspend fun updateByName(name: String, update: FunctionUpdate): FunctionDoc? {
        val existing = findByName(name) ?: return null
        
        val updatedFunction = existing.copy(
            description = update.description ?: existing.description,
            deps = update.deps ?: existing.deps,
            code = update.code ?: existing.code,
            updatedAt = Instant.now()
        )
        
        cache.put(name, updatedFunction)
        return updatedFunction
    }

    override suspend fun deleteByName(name: String): Boolean {
        val existed = cache.getIfPresent(name) != null
        cache.invalidate(name)
        return existed
    }

    override suspend fun existsByName(name: String): Boolean {
        return cache.getIfPresent(name) != null
    }

    /**
     * Clears the cache (useful for testing).
     */
    fun clear() {
        cache.invalidateAll()
    }

    /**
     * Returns the current cache size.
     */
    fun size(): Int = cache.asMap().size
}
