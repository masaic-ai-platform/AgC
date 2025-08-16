package ai.masaic.platform.api.config

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.config.QdrantVectorProperties
import ai.masaic.openresponses.api.config.VectorSearchConfigProperties
import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.api.model.ModelInfo
import ai.masaic.openresponses.api.model.PyInterpreterServer
import ai.masaic.openresponses.api.repository.VectorStoreRepository
import ai.masaic.openresponses.api.service.VectorStoreFileManager
import ai.masaic.openresponses.api.service.embedding.EmbeddingService
import ai.masaic.openresponses.api.service.embedding.OpenAIProxyEmbeddingService
import ai.masaic.openresponses.api.service.rerank.RerankerService
import ai.masaic.openresponses.api.service.search.*
import ai.masaic.openresponses.api.support.service.TelemetryService
import ai.masaic.openresponses.tool.ToolService
import ai.masaic.openresponses.tool.mcp.MCPToolExecutor
import ai.masaic.platform.api.interpreter.CodeRunnerService
import ai.masaic.platform.api.interpreter.PythonCodeRunnerService
import ai.masaic.platform.api.repository.*
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.service.PlatformHybridSearchService
import ai.masaic.platform.api.service.PlatformQdrantVectorSearchProvider
import ai.masaic.platform.api.service.PlatformVectorStoreService
import ai.masaic.platform.api.tools.*
import ai.masaic.platform.api.user.AuthConfig
import ai.masaic.platform.api.user.AuthConfigProperties
import ai.masaic.platform.api.utils.PlatformPayloadFormatter
import ai.masaic.platform.api.validation.PlatformRequestValidator
import com.fasterxml.jackson.databind.ObjectMapper
import io.qdrant.client.QdrantClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import java.time.Instant

@Profile("platform")
@Configuration
class PlatformCoreConfig {
    @Value("\${platform.deployment.apiKey:na}")
    private val modelApiKey = ""

    @Value("\${platform.deployment.model:openai@gpt-4.1-mini}")
    private val model = "openai@gpt-4.1-mini"

    @Bean
    fun systemSettings(): ModelSettings = if (modelApiKey == "na") ModelSettings() else ModelSettings(SystemSettingsType.DEPLOYMENT_TIME, modelApiKey, model)

    @Bean
    fun funDefGeneratorTool(
        @Lazy modelService: ModelService,
        modelSettings: ModelSettings,
    ) = FunDefGenerationTool(modelService, modelSettings)

    @Bean
    fun funReqGatheringTool(
        @Lazy modelService: ModelService,
        modelSettings: ModelSettings,
    ) = FunReqGatheringTool(modelService, modelSettings)

    @Bean
    fun mockFunSaveTool(mockFunctionRepository: MockFunctionRepository) = MockFunSaveTool(mockFunctionRepository)

    @Bean
    fun mockGenerationTool(
        @Lazy modelService: ModelService,
        modelSettings: ModelSettings,
    ) = MockGenerationTool(modelService, modelSettings)

    @Bean
    fun mockSaveTool(mocksRepository: MocksRepository) = MockSaveTool(mocksRepository)

    @Bean
    fun modelTestTool() = ModelTestTool()

    @Bean
    fun platformNativeToolRegistry(
        objectMapper: ObjectMapper,
        responseStore: ResponseStore,
        platformNativeTools: List<PlatformNativeTool>,
        @Lazy codeRunnerService: CodeRunnerService
    ) = PlatformNativeToolRegistry(
        objectMapper,
        responseStore,
        platformNativeTools,
        codeRunnerService
    )

    @Bean
    fun mcpClientFactory(
        mcpMockServerRepository: McpMockServerRepository,
        mockFunctionRepository: MockFunctionRepository,
        mocksRepository: MocksRepository,
        modelSettings: ModelSettings,
        @Lazy modelService: ModelService,
    ) = PlatformMcpClientFactory(mcpMockServerRepository, mockFunctionRepository, mocksRepository, modelSettings, modelService)

    @Bean
    fun platformMcpService(
        mcpMockServerRepository: McpMockServerRepository,
        mockFunctionRepository: MockFunctionRepository,
        mocksRepository: MocksRepository,
    ) = PlatformMcpService(mcpMockServerRepository, mockFunctionRepository, mocksRepository)

    @Bean
    fun payloadFormatter(toolService: ToolService, mapper: ObjectMapper)= PlatformPayloadFormatter(toolService, mapper)

    @Bean
    fun platformRequestValidator(vectorStoreService: VectorStoreService, responseStore: ResponseStore, platformInfo: PlatformInfo) = PlatformRequestValidator(vectorStoreService, responseStore, platformInfo)

    @Bean
    fun platformInfo(
        @Value(value = "\${open-responses.store.vector.search.provider:file}") vectorSearchProviderType: String,
        buildProperties: BuildProperties,
        modelSettings: ModelSettings,
        pyInterpreterSettings: PyInterpreterSettings,
        configProperties: AuthConfigProperties,
    ): PlatformInfo {
        val vectorStoreInfo =
            if (vectorSearchProviderType == "qdrant") VectorStoreInfo(true) else VectorStoreInfo(false)
        return PlatformInfo(
            version = "v${buildProperties.version}",
            buildTime = buildProperties.time,
            modelSettings = ModelSettings(modelSettings.settingsType, "", ""),
            vectorStoreInfo = vectorStoreInfo,
            authConfig = AuthConfig(configProperties.enabled),
            pyInterpreterSettings = if (pyInterpreterSettings.systemSettingsType == SystemSettingsType.DEPLOYMENT_TIME) /*to avoid api key leak*/ PyInterpreterSettings(
                SystemSettingsType.DEPLOYMENT_TIME
            ) else PyInterpreterSettings()
        )
    }

    @Configuration
    @Profile("platform")
    @EnableConfigurationProperties(CodeInterpreterServerProperties::class)
    class PythonCodeRunnerConfiguration {
        @Bean
        fun pythonCodeRunnerService(pyInterpreterSettings: PyInterpreterSettings, toolService: ToolService, mcpToolExecutor: MCPToolExecutor): CodeRunnerService {
            val codeRunnerService = PythonCodeRunnerService(pyInterpreterSettings, toolService, mcpToolExecutor)
            return codeRunnerService
        }

        @Bean
        fun pyInterpreterSettings(
            codeInterpreterServer: CodeInterpreterServerProperties,
        ): PyInterpreterSettings {
            if(codeInterpreterServer.name.isNullOrBlank() && codeInterpreterServer.url.isNullOrBlank() && codeInterpreterServer.apiKey.isNullOrBlank()) {
                return PyInterpreterSettings(SystemSettingsType.RUNTIME)
            }
            require(!codeInterpreterServer.name.isNullOrBlank()) {"property platform.deployment.code.interpreter.name is not set"}
            require(!codeInterpreterServer.url.isNullOrBlank()) {"property platform.deployment.code.interpreter.url is not set"}
            require(!codeInterpreterServer.apiKey.isNullOrBlank()) {"property platform.deployment.code.interpreter.apiKey is not set"}
            return PyInterpreterSettings(SystemSettingsType.DEPLOYMENT_TIME, PyInterpreterServer(serverLabel = codeInterpreterServer.name, url = codeInterpreterServer.url, apiKey = codeInterpreterServer.apiKey))
        }

        @Bean
        @ConditionalOnBean(PythonCodeRunnerService::class)
        fun pythonCodeRunnerTool(pythonCodeRunnerService: PythonCodeRunnerService) = PythonCodeRunnerTool(pythonCodeRunnerService)
    }

    @Configuration
    @Profile("platform")
    @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "mongodb")
    class MongoRepositoryConfiguration {
        @Bean
        fun mcpMockServerRepository(mongoTemplate: ReactiveMongoTemplate) = MongoMcpMockServerRepository(mongoTemplate)

        @Bean
        fun mockFunctionRepository(mongoTemplate: ReactiveMongoTemplate) = MongoMockFunctionRepository(mongoTemplate)

        @Bean
        fun mocksRepository(mongoTemplate: ReactiveMongoTemplate) = MongoMocksRepository(mongoTemplate)
    }

    @Configuration
    @Profile("platform")
    @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "in-memory", matchIfMissing = true)
    class InMemoryRepositoryConfiguration {
        @Bean
        fun mcpMockServerRepository() = InMemoryMcpMockServerRepository()

        @Bean
        fun mockFunctionRepository() = InMemoryMockFunctionRepository()

        @Bean
        fun mocksRepository() = InMemoryMocksRepository()
    }

    @Configuration
    @Profile("platform")
    @ConditionalOnProperty(name = ["open-responses.store.vector.search.provider"], havingValue = "qdrant")
    class PlatformQdrantSetupConfiguration {
        @Bean
        fun platformVectorStoreService(
            vectorStoreFileManager: VectorStoreFileManager,
            vectorStoreRepository: VectorStoreRepository,
            vectorSearchProvider: PlatformQdrantVectorSearchProvider,
            telemetryService: TelemetryService,
            hybridSearchServiceHelper: HybridSearchServiceHelper,
        ) = PlatformVectorStoreService(
            vectorStoreFileManager,
            vectorStoreRepository,
            vectorSearchProvider,
            telemetryService,
            hybridSearchServiceHelper,
        )

        @Bean
        fun platformHybridSearchService(
            vectorSearchProvider: PlatformQdrantVectorSearchProvider,
            @Value("\${open-responses.store.vector.repository.type}")
            repositoryType: String,
            luceneIndexService: LuceneIndexService? = null,
            mongoTemplate: ReactiveMongoTemplate? = null,
            rerankerService: RerankerService? = null,
        ) = PlatformHybridSearchService(
            vectorSearchProvider,
            repositoryType,
            luceneIndexService,
            mongoTemplate,
            rerankerService,
        )

        @Bean
        fun platformQdrantVectorSearchProvider(
            embeddingService: EmbeddingService,
            qdrantProperties: QdrantVectorProperties,
            vectorSearchProperties: VectorSearchConfigProperties,
            hybridSearchServiceHelper: HybridSearchServiceHelper,
            client: QdrantClient,
            proxyEmbeddingService: OpenAIProxyEmbeddingService,
        ) = PlatformQdrantVectorSearchProvider(embeddingService, qdrantProperties, vectorSearchProperties, hybridSearchServiceHelper, client, proxyEmbeddingService)
    }

    @Configuration
    @Profile("platform")
    @ConditionalOnProperty(name = ["open-responses.store.vector.search.provider"], havingValue = "file", matchIfMissing = true)
    class PlatformWithoutQdrantConfiguration {
        @Bean
        fun platformVectorStoreService(
            vectorStoreFileManager: VectorStoreFileManager,
            vectorStoreRepository: VectorStoreRepository,
            vectorSearchProvider: VectorSearchProvider,
            telemetryService: TelemetryService,
            hybridSearchServiceHelper: HybridSearchServiceHelper,
        ) = VectorStoreService(
            vectorStoreFileManager,
            vectorStoreRepository,
            vectorSearchProvider,
            telemetryService,
            hybridSearchServiceHelper,
        )

        @Bean
        fun platformHybridSearchService(
            vectorSearchProvider: VectorSearchProvider,
            @Value("\${open-responses.store.vector.repository.type}")
            repositoryType: String,
            luceneIndexService: LuceneIndexService? = null,
            mongoTemplate: ReactiveMongoTemplate? = null,
            rerankerService: RerankerService? = null,
        ) = HybridSearchService(
            vectorSearchProvider,
            repositoryType,
            luceneIndexService,
            mongoTemplate,
            rerankerService,
        )
    }
}

data class ModelSettings(
    val settingsType: SystemSettingsType,
    var apiKey: String,
    var model: String,
) {
    var bearerToken: String
    var qualifiedModelName: String = model

    init {
        if (model.contains("@")) model = model.split("@")[1]
        if (apiKey.startsWith("Bearer ")) apiKey = apiKey.removePrefix("Bearer ").trim()

        bearerToken = "Bearer $apiKey"
    }

    constructor() : this(SystemSettingsType.RUNTIME, "", "")
    constructor(modelApiKey: String, model: String) : this(SystemSettingsType.RUNTIME, modelApiKey, model)

    fun resolveSystemSettings(modelInfo: ModelInfo?): ModelSettings =
        if (this.settingsType == SystemSettingsType.DEPLOYMENT_TIME) {
            this
        } else {
            requireNotNull(modelInfo) { "apiKey and model is required" }
            requireNotNull(modelInfo.bearerToken) { "apiKey required, can't be null or blank" }
            requireNotNull(modelInfo.model) { "model required, can't be null or blank" }
            ModelSettings(modelInfo.bearerToken, modelInfo.model)
        }

    fun resolveSystemSettings(modelSettings: ModelSettings?): ModelSettings =
        if (this.settingsType == SystemSettingsType.DEPLOYMENT_TIME) {
            this
        } else {
            requireNotNull(modelSettings) { "apiKey and model is required" }
            requireNotNull(modelSettings.apiKey) { "apiKey required, can't be null or blank" }
            requireNotNull(modelSettings.model) { "model required, can't be null or blank" }
            ModelSettings(modelSettings.apiKey, modelSettings.model)
        }
}

enum class SystemSettingsType {
    RUNTIME,
    DEPLOYMENT_TIME,
}

@ConfigurationProperties("platform.deployment.code.interpreter")
data class CodeInterpreterServerProperties(
    val name: String ?= null,
    val url: String ?= null,
    val apiKey: String ?= null
)

data class PyInterpreterSettings(
    val systemSettingsType: SystemSettingsType = SystemSettingsType.RUNTIME,
    val pyInterpreterServer: PyInterpreterServer? = null
) {
    fun mcpTool(): MCPTool {
        require(pyInterpreterServer != null) { "pyInterpreterServer can't be null" }
        return mcpTool(pyInterpreterServer)
    }

    fun mcpTool(pyInterpreterServer: PyInterpreterServer): MCPTool {
        return MCPTool(
            type = "mcp",
            serverLabel = pyInterpreterServer.url,
            serverUrl = pyInterpreterServer.url,
            headers = mapOf("Authorization" to "Bearer ${pyInterpreterServer.apiKey}")
        )
    }
}

data class PlatformInfo(
    val version: String,
    val buildTime: Instant,
    val modelSettings: ModelSettings,
    val vectorStoreInfo: VectorStoreInfo,
    val authConfig: AuthConfig,
    val pyInterpreterSettings: PyInterpreterSettings
)

data class VectorStoreInfo(
    val isEnabled: Boolean,
)
