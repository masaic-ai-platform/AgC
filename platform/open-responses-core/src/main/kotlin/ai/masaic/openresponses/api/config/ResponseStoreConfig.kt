package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.MongoResponseStore
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@Configuration
@ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "mongodb", matchIfMissing = false)
class ResponseStoreConfig {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun mongoResponseStore(
        reactiveMongoTemplate: ReactiveMongoTemplate,
        objectMapper: ObjectMapper,
    ): MongoResponseStore {
        logger.info { "Creating MongoResponseStore bean with MongoDB backend" }
        return MongoResponseStore(reactiveMongoTemplate, objectMapper)
    }
}
