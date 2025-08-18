package ai.masaic.platform.api.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Function Registry repositories.
 * Ensures proper repository selection based on storage type.
 */
@Configuration
class FunctionRegistryConfig {
    /**
     * MongoDB repository configuration.
     * Only enabled when open-responses.store.type=mongodb
     */
    @Configuration
    @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "mongodb")
    class MongoRepositoryConfiguration {
        // MongoFunctionRegistryRepository is automatically configured via @Repository annotation
        // and ReactiveMongoTemplate injection
    }

    /**
     * In-memory repository configuration.
     * Only enabled when open-responses.store.type=in-memory (default)
     */
    @Configuration
    @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "in-memory", matchIfMissing = true)
    class InMemoryRepositoryConfiguration {
        // InMemoryFunctionRegistryRepository is automatically configured via @Repository annotation
    }
}
