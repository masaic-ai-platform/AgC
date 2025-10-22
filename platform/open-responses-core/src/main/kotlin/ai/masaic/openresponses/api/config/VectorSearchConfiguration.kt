package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.repository.VectorStoreRepository
import ai.masaic.openresponses.api.service.VectorStoreFileManager
import ai.masaic.openresponses.api.service.embedding.EmbeddingService
import ai.masaic.openresponses.api.service.rerank.RerankerService
import ai.masaic.openresponses.api.service.search.*
import ai.masaic.openresponses.api.support.service.TelemetryService
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * Configuration for vector search providers.
 */
@Configuration
class VectorSearchConfiguration {
    @Bean
    @ConditionalOnProperty(name = ["open-responses.store.vector.search.provider"], havingValue = "qdrant")
    fun qdrantClient(qdrantProperties: QdrantVectorProperties): QdrantClient =
        QdrantClient(
            QdrantGrpcClient
                .newBuilder(
                    qdrantProperties.host,
                    qdrantProperties.port,
                    qdrantProperties.useTls,
                ).apply { qdrantProperties.apiKey?.let { withApiKey(it) } }
                .build(),
        )

    @Bean
    @ConditionalOnMissingBean
    fun hybridSearchService(
        vectorSearchProvider: VectorSearchProvider,
        vectorRepositoryProperties: VectorRepositoryProperties,
        luceneIndexService: LuceneIndexService? = null,
        mongoTemplate: ReactiveMongoTemplate? = null,
        rerankerService: RerankerService? = null,
    ) = HybridSearchService(vectorSearchProvider, vectorRepositoryProperties, luceneIndexService, mongoTemplate, rerankerService)

    @Bean
    @ConditionalOnProperty(name = ["open-responses.store.vector.search.provider"], havingValue = "qdrant")
    @ConditionalOnMissingBean
    fun qdrantVectorSearchProvider(
        embeddingService: EmbeddingService,
        qdrantProperties: QdrantVectorProperties,
        vectorSearchProperties: VectorSearchConfigProperties,
        hybridSearchServiceHelper: HybridSearchServiceHelper,
        client: QdrantClient,
    ) = QdrantVectorSearchProvider(embeddingService, qdrantProperties, vectorSearchProperties, hybridSearchServiceHelper, client)

    @Bean
    @ConditionalOnMissingBean
    fun vectorStoreService(
        vectorStoreFileManager: VectorStoreFileManager,
        vectorStoreRepository: VectorStoreRepository,
        vectorSearchProvider: VectorSearchProvider,
        telemetryService: TelemetryService,
        hybridSearchServiceHelper: HybridSearchServiceHelper,
    ) = VectorStoreService(vectorStoreFileManager, vectorStoreRepository, vectorSearchProvider, telemetryService, hybridSearchServiceHelper)
}
