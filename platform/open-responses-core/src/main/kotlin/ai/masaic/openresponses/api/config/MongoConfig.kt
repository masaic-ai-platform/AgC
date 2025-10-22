package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.MongoResponseStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.reactivestreams.client.MongoClient
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * MongoDB configuration class.
 *
 * This class configures the MongoDB connection and related beans.
 * Uses reactive MongoDB exclusively to avoid unnecessary blocking operations.
 */
@Configuration
@EnableReactiveMongoRepositories(basePackages = ["ai.masaic.openresponses.api.repository", "ai.masaic.openevals.api.repository"])
@EnableReactiveMongoAuditing
@EnableTransactionManagement
@ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "mongodb", matchIfMissing = false)
@EnableConfigurationProperties(MongoConfigProperties::class)
@ImportAutoConfiguration(
    classes = [
        org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration::class,
    ],
)
class MongoConfig(
    private val mongoConfigProperties: MongoConfigProperties,
) : AbstractReactiveMongoConfiguration() {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun mongoResponseStore(
        reactiveMongoTemplate: ReactiveMongoTemplate,
        objectMapper: ObjectMapper,
    ): MongoResponseStore {
        logger.debug { "Creating MongoResponseStore bean" }
        return MongoResponseStore(reactiveMongoTemplate, objectMapper)
    }

    /**
     * Configures a transaction manager for MongoDB.
     */
    @Bean
    fun transactionManager(factory: ReactiveMongoDatabaseFactory): ReactiveMongoTransactionManager = ReactiveMongoTransactionManager(factory)

    override fun getDatabaseName(): String {
        logger.debug { "MongoDB database name: $databaseName" }
        return mongoConfigProperties.database
    }

    override fun reactiveMongoClient(): MongoClient {
        logger.debug { "MongoDB URI: ${mongoConfigProperties.uri}" }
        return com.mongodb.reactivestreams.client.MongoClients
            .create(mongoConfigProperties.uri)
    }
}

@ConfigurationProperties("open-responses.mongodb")
data class MongoConfigProperties(
    val uri: String = "mongodb://localhost:27017/openresponses",
    val database: String = "openresponses",
)
