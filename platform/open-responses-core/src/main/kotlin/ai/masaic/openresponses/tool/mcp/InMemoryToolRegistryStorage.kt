package ai.masaic.openresponses.tool.mcp

import ai.masaic.openresponses.api.config.ToolsCaffeineCacheConfig
import ai.masaic.openresponses.tool.ToolDefinition
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

/**
 * In-memory tool registry storage implementation using Caffeine cache.
 *
 * This implementation provides fast in-memory access with optional TTL.
 * Suitable for single-instance deployments or development environments.
 */
open class InMemoryToolRegistryStorage(
    caffeineCacheConfig: ToolsCaffeineCacheConfig,
) : ToolRegistryStorage {
    private val log = KotlinLogging.logger { }

    private val cache: Cache<String, ToolDefinition> =
        Caffeine
            .newBuilder()
            .maximumSize(caffeineCacheConfig.maxSize)
            .expireAfterWrite(caffeineCacheConfig.ttlMinutes, TimeUnit.MINUTES)
            .build()

    override suspend fun <T : ToolDefinition> add(
        toolDefinition: T,
        type: Class<T>,
    ) {
        val key = buildKey(toolDefinition.name, type)
        cache.put(key, toolDefinition)
        log.debug("Added tool '${toolDefinition.name}' to cache")
    }

    override suspend fun <T : ToolDefinition> get(
        name: String,
        type: Class<T>,
    ): T? {
        val key = buildKey(name, type)
        val tool = cache.getIfPresent(key)
        val result = if (tool != null && type.isInstance(tool)) type.cast(tool) else null
        log.debug("Retrieved tool '$name' (type: ${type.simpleName}) from cache: ${if (tool != null) "found" else "not found"}")
        return result
    }

    override suspend fun <T : ToolDefinition> remove(
        name: String,
        type: Class<T>,
    ) {
        val key = buildKey(name, type)
        cache.invalidate(key)
        log.debug("Removed tool '$name' from cache")
    }

    open suspend fun buildKey(
        name: String,
        type: Class<*>,
    ) = "$name:${type.canonicalName}"
}
