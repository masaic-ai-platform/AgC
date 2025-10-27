package ai.masaic.platform.api.config

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.config.DeploymentSettings
import ai.masaic.openresponses.api.config.QdrantVectorProperties
import ai.masaic.openresponses.api.config.VectorRepositoryProperties
import ai.masaic.openresponses.api.config.VectorSearchConfigProperties
import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.repository.VectorStoreRepository
import ai.masaic.openresponses.api.service.ResponseFacadeService
import ai.masaic.openresponses.api.service.VectorStoreFileManager
import ai.masaic.openresponses.api.service.embedding.EmbeddingService
import ai.masaic.openresponses.api.service.embedding.OpenAIProxyEmbeddingService
import ai.masaic.openresponses.api.service.rerank.RerankerService
import ai.masaic.openresponses.api.service.search.*
import ai.masaic.openresponses.api.support.service.TelemetryService
import ai.masaic.openresponses.tool.NativeToolRegistry
import ai.masaic.openresponses.tool.PlugableToolAdapter
import ai.masaic.openresponses.tool.ToolService
import ai.masaic.openresponses.tool.mcp.MCPToolExecutor
import ai.masaic.openresponses.tool.mcp.MCPToolRegistry
import ai.masaic.openresponses.tool.mcp.McpClientFactory
import ai.masaic.openresponses.tool.mcp.ToolRegistryStorage
import ai.masaic.platform.api.interpreter.CodeRunnerService
import ai.masaic.platform.api.interpreter.PythonCodeRunnerService
import ai.masaic.platform.api.model.ModelProvider
import ai.masaic.platform.api.registry.functions.FunctionRegistryRepository
import ai.masaic.platform.api.registry.functions.FunctionRegistryService
import ai.masaic.platform.api.registry.functions.FunctionRegistryValidator
import ai.masaic.platform.api.repository.*
import ai.masaic.platform.api.service.*
import ai.masaic.platform.api.telemtetry.PlatformTelemetryService
import ai.masaic.platform.api.telemtetry.langfuse.LangfuseTelemetryService
import ai.masaic.platform.api.tools.*
import ai.masaic.platform.api.utils.PlatformPayloadFormatter
import ai.masaic.platform.api.validation.PlatformRequestValidator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.OpenTelemetry
import io.qdrant.client.QdrantClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ResourceLoader
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import java.net.URI
import java.time.Instant

@Configuration
@EnableConfigurationProperties(ProviderApiKeysProperties::class)
class PlatformCoreConfig {
    @Value("\${platform.deployment.apiKey:na}")
    private val modelApiKey = ""

    @Value("\${platform.deployment.model:openai@gpt-4.1-mini}")
    private val model = "openai@gpt-4.1-mini"

    companion object {
        fun loadProviders(): Set<ModelProvider> {
            val resource = ClassPathResource("model-providers.json")
            val jsonContent = resource.inputStream.bufferedReader().use { it.readText() }
            val providersList: List<ModelProvider> = jacksonObjectMapper().readValue(jsonContent)
            return providersList.toSet()
        }
    }

    @Bean
    fun modelSettings(
        providerApiKeysProperties: ProviderApiKeysProperties,
    ): ModelSettings =
        if (modelApiKey == "na") {
            ModelSettings(providerApiKeysProperties.providers)
        } else {
            ModelSettings(SystemSettingsType.DEPLOYMENT_TIME, modelApiKey, model, providerApiKeysProperties.providers)
        }

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
        @Lazy codeRunnerService: CodeRunnerService,
        plugableToolAdapter: PlugableToolAdapter,
        toolStorage: ToolRegistryStorage,
    ) = PlatformNativeToolRegistry(
        objectMapper,
        responseStore,
        platformNativeTools,
        codeRunnerService,
        plugableToolAdapter,
        toolStorage,
    )

    @Bean
    @ConditionalOnMissingBean(McpClientFactory::class)
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
    fun payloadFormatter(
        toolService: PlatformToolService,
        mapper: ObjectMapper,
    ) = PlatformPayloadFormatter(toolService, mapper)

    @Bean
    fun platformToolService(
        mcpToolRegistry: MCPToolRegistry,
        mcpToolExecutor: MCPToolExecutor,
        resourceLoader: ResourceLoader,
        nativeToolRegistry: NativeToolRegistry,
        objectMapper: ObjectMapper,
        plugableToolAdapter: PlugableToolAdapter,
        pluggedToolsRegistry: PluggedToolsRegistry,
    ) = PlatformToolService(mcpToolRegistry, mcpToolExecutor, resourceLoader, nativeToolRegistry, objectMapper, plugableToolAdapter, pluggedToolsRegistry)

    @Bean
    fun platformRequestValidator(
        vectorStoreService: VectorStoreService,
        responseStore: ResponseStore,
        platformInfo: PlatformInfo,
        deploymentSettings: DeploymentSettings,
    ) = PlatformRequestValidator(vectorStoreService, responseStore, platformInfo, deploymentSettings)

    @Bean
    @ConditionalOnMissingBean
    fun platformInfo(
        @Value(value = "\${open-responses.store.vector.search.provider:file}") vectorSearchProviderType: String,
        buildProperties: BuildProperties,
        modelSettings: ModelSettings,
        pyInterpreterSettings: PyInterpreterSettings,
        partners: Partners,
        @Value("\${platform.deployment.oauth.redirectAgcHost:na}") agcPlatformRedirectBaseUrl: String = "na",
        @Value("\${platform.deployment.oauth.agcUiHost:na}") agcUiHost: String = "na",
        @Value("\${platform.deployment.agc-cs-runtime.path:/app/agc-client-runtime/java-sdk}") agcRuntimePath: String,
        @Value("\${platform.deployment.agc-cs-runtime.securitykey:na}") securityKey: String,
        @Value("\${platform.deployment.multiplug.enabled:false}") multiPlugEnabled: Boolean,
        @Value("\${platform.deployment.environment:local}") env: String,
        @Value("\${spring.application.name:agc-platform}") appName: String,
    ): PlatformInfo {
        val vectorStoreInfo =
            if (vectorSearchProviderType == "qdrant") VectorStoreInfo(true) else VectorStoreInfo(false)

        val oAuthRedirectSpecs =
            if (agcPlatformRedirectBaseUrl != "na" && agcUiHost != "na") {
                OAuthRedirectSpecs(
                    URI(agcPlatformRedirectBaseUrl),
                    URI(agcUiHost),
                )
            } else {
                OAuthRedirectSpecs()
            }

        if (multiPlugEnabled && securityKey == "na") throw IllegalStateException("property platform.deployment.agc-cs-runtime.securityKey is not defined")

        return PlatformInfo(
            env = env,
            appName = appName,
            version = "v${buildProperties.version}",
            buildTime = buildProperties.time,
            modelSettings = ModelSettings(modelSettings.settingsType, "", ""),
            vectorStoreInfo = vectorStoreInfo,
            authConfig = AuthConfig(false),
            pyInterpreterSettings =
                if (pyInterpreterSettings.systemSettingsType == SystemSettingsType.DEPLOYMENT_TIME) {
                    // to avoid api key leak
                    PyInterpreterSettings(
                        SystemSettingsType.DEPLOYMENT_TIME,
                    )
                } else {
                    PyInterpreterSettings()
                },
            partners = partners,
            oAuthRedirectSpecs = oAuthRedirectSpecs,
            agentClientSideRuntimeConfig = AgentClientSideRuntimeConfig(agcRuntimePath, securityKey, multiPlugEnabled),
        )
    }

    @Bean
    fun mcpClientStore() = PlatformCaffeineMcpClientStore()

    @Bean
    fun agentService(
        agentRepository: AgentRepository,
        functionRegistryService: FunctionRegistryService,
        platformMcpService: PlatformMcpService,
    ) = AgentService(agentRepository, functionRegistryService, platformMcpService)

    @Bean
    fun funRegistryValidator() = FunctionRegistryValidator()

    @Bean
    fun funRegService(
        repository: FunctionRegistryRepository,
        functionRegistryValidator: FunctionRegistryValidator,
    ) = FunctionRegistryService(repository, functionRegistryValidator)

    @Bean
    fun systemPromptGeneratorTool(
        modelSettings: ModelSettings,
        @Lazy modelService: ModelService,
    ) = SystemPromptGeneratorTool(modelSettings, modelService)

    @Bean
    fun toolSelectorTool(
        modelSettings: ModelSettings,
        @Lazy modelService: ModelService,
        platformMcpService: PlatformMcpService,
        funRegService: FunctionRegistryService,
        @Lazy toolService: ToolService,
    ) = ToolSelectorTool(modelSettings, modelService, platformMcpService, funRegService, toolService)

    @Bean
    fun agentBuilderChatService(
        responseFacadeService: ResponseFacadeService,
        agentService: AgentService,
    ) = AgentBuilderChatService(responseFacadeService, agentService)

    @Bean
    fun askAgentService(
        agentService: AgentService,
        responseFacadeService: ResponseFacadeService,
        modelSettings: ModelSettings,
        pyInterpreterSettings: PyInterpreterSettings,
    ) = AskAgentService(agentService, responseFacadeService, modelSettings, pyInterpreterSettings)

    @Bean
    @ConditionalOnMissingBean(TelemetryService::class)
    fun platformTelemetryService(
        observationRegistry: ObservationRegistry,
        openTelemetry: OpenTelemetry,
        meterRegistry: MeterRegistry,
    ) = PlatformTelemetryService(observationRegistry, openTelemetry, meterRegistry)

    @Bean
    @ConditionalOnMissingBean(Partners::class)
    fun noPartner() = emptyList<Partner>()

    @Bean
    @ConditionalOnProperty(name = ["platform.deployment.agent.bootstrap.enabled"], havingValue = "true", matchIfMissing = true)
    fun agentB(
        agentService: AgentService,
        platformMcpService: PlatformMcpService,
        mockFunctionRepository: MockFunctionRepository,
        mocksRepository: MocksRepository,
        functionRegistryService: FunctionRegistryService,
    ) = AgentBootstrapService(agentService, platformMcpService, mockFunctionRepository, mocksRepository, functionRegistryService)

    @Configuration
    @EnableConfigurationProperties(CodeInterpreterServerProperties::class)
    class PythonCodeRunnerConfiguration {
        @Bean
        fun pythonCodeRunnerService(
            pyInterpreterSettings: PyInterpreterSettings,
            toolService: ToolService,
            mcpToolExecutor: MCPToolExecutor,
        ): CodeRunnerService {
            val codeRunnerService = PythonCodeRunnerService(pyInterpreterSettings, toolService, mcpToolExecutor)
            return codeRunnerService
        }

        @Bean
        fun pyInterpreterSettings(
            codeInterpreterServer: CodeInterpreterServerProperties,
        ): PyInterpreterSettings {
            if (codeInterpreterServer.name.isNullOrBlank() && codeInterpreterServer.url.isNullOrBlank() && codeInterpreterServer.apiKey.isNullOrBlank()) {
                return PyInterpreterSettings(SystemSettingsType.RUNTIME)
            }
            require(!codeInterpreterServer.name.isNullOrBlank()) { "property platform.deployment.code.interpreter.name is not set" }
            require(!codeInterpreterServer.url.isNullOrBlank()) { "property platform.deployment.code.interpreter.url is not set" }
            require(!codeInterpreterServer.apiKey.isNullOrBlank()) { "property platform.deployment.code.interpreter.apiKey is not set" }
            return PyInterpreterSettings(SystemSettingsType.DEPLOYMENT_TIME, PyInterpreterServer(serverLabel = codeInterpreterServer.name, url = codeInterpreterServer.url, apiKey = codeInterpreterServer.apiKey))
        }
    }

    @Configuration
    @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "mongodb")
    class MongoRepositoryConfiguration {
        @Bean
        fun mcpMockServerRepository(mongoTemplate: ReactiveMongoTemplate) = MongoMcpMockServerRepository(mongoTemplate)

        @Bean
        fun mockFunctionRepository(mongoTemplate: ReactiveMongoTemplate) = MongoMockFunctionRepository(mongoTemplate)

        @Bean
        fun mocksRepository(mongoTemplate: ReactiveMongoTemplate) = MongoMocksRepository(mongoTemplate)

        @Bean
        fun agentRepository(mongoTemplate: ReactiveMongoTemplate) = MongoAgentRepository(mongoTemplate)
    }

    @Configuration
    @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "in-memory", matchIfMissing = true)
    class InMemoryRepositoryConfiguration {
        @Bean
        fun mcpMockServerRepository() = InMemoryMcpMockServerRepository()

        @Bean
        fun mockFunctionRepository() = InMemoryMockFunctionRepository()

        @Bean
        fun mocksRepository() = InMemoryMocksRepository()

        @Bean
        fun agentRepository() = InMemoryAgentRepository()
    }

    @Configuration
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
            vectorRepositoryProperties: VectorRepositoryProperties,
            luceneIndexService: LuceneIndexService? = null,
            mongoTemplate: ReactiveMongoTemplate? = null,
            rerankerService: RerankerService? = null,
        ) = PlatformHybridSearchService(
            vectorSearchProvider,
            vectorRepositoryProperties,
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
            vectorRepositoryProperties: VectorRepositoryProperties,
            luceneIndexService: LuceneIndexService? = null,
            mongoTemplate: ReactiveMongoTemplate? = null,
            rerankerService: RerankerService? = null,
        ) = HybridSearchService(
            vectorSearchProvider,
            vectorRepositoryProperties,
            luceneIndexService,
            mongoTemplate,
            rerankerService,
        )
    }
}

object PartnerGroup {
    val qdrant =
        Partner(
            code = "qdrant",
            name = "Qdrant",
            category = PartnerCategory.VECTOR_DB,
            enabled = true,
            deploymentLink = "https://cloud.qdrant.io",
        )

    val signoz =
        Partner(
            code = "signoz",
            name = "Signoz",
            category = PartnerCategory.OBSERVABILITY,
            enabled = true,
            deploymentLink = "https://signoz.io",
        )

    val langfuse =
        Partner(
            code = "langfuse",
            name = "Langfuse",
            category = PartnerCategory.EVALS,
            enabled = true,
            deploymentLink = "https://cloud.langfuse.com/",
        )

    val e2B =
        Partner(
            code = "e2b",
            name = "AgC Macro",
            category = PartnerCategory.COMPUTE,
            enabled = true,
            deploymentLink = "",
        )
}

@Configuration
class PartnersConfiguration(
    private val env: org.springframework.core.env.Environment,
) {
    @Bean
    fun partners(
        @Value(value = "\${otel.sdk.disabled:true}") otelSdkDisabled: Boolean = true,
        @Value(value = "\${otel.exporter.otlp.endpoint:http://localhost:4318}") otelEndpoint: String? = null,
        @Value(value = "\${open-responses.store.vector.search.provider:file}") vectorSearchProviderType: String,
    ): Partners {
        val partners = mutableListOf<Partner>()

        val forceLangfuse =
            env.acceptsProfiles(
                org.springframework.core.env.Profiles
                    .of("langfuse"),
            )
        val forceSignoz =
            env.acceptsProfiles(
                org.springframework.core.env.Profiles
                    .of("signoz"),
            )

        if (forceLangfuse || (!otelSdkDisabled && otelEndpoint?.contains("langfuse") == true)) {
            partners += PartnerGroup.langfuse
        }

        if (forceSignoz || (!otelSdkDisabled && otelEndpoint?.contains("signoz") == true)) {
            partners += PartnerGroup.signoz
        }

        val vectorStoreInfo =
            if (vectorSearchProviderType == "qdrant") VectorStoreInfo(true) else VectorStoreInfo(false)

        // Vector store
        if (vectorStoreInfo.isEnabled) {
            partners += PartnerGroup.qdrant
        }

        partners += PartnerGroup.e2B
        return Partners(details = partners)
    }

    @Bean
    @Profile("langfuse")
    fun langfuseTelemetryService(
        observationRegistry: ObservationRegistry,
        openTelemetry: OpenTelemetry,
        meterRegistry: MeterRegistry,
    ) = LangfuseTelemetryService(observationRegistry, openTelemetry, meterRegistry)
}

@ConfigurationProperties("platform.deployment.code.interpreter")
data class CodeInterpreterServerProperties(
    val name: String? = null,
    val url: String? = null,
    val apiKey: String? = null,
)

@ConfigurationProperties("platform.deployment")
data class ProviderApiKeysProperties(
    val providers: Map<String, ProviderConfig> = emptyMap(),
) {
    fun getApiKey(providerName: String): String? = providers[providerName]?.apiKey ?: providers[providerName.lowercase()]?.apiKey
}

data class PyInterpreterSettings(
    val systemSettingsType: SystemSettingsType = SystemSettingsType.RUNTIME,
    val pyInterpreterServer: PyInterpreterServer? = null,
    val isEnabled: Boolean = true,
) {
    fun mcpTool(): MCPTool {
        require(pyInterpreterServer != null) { "pyInterpreterServer can't be null" }
        return mcpTool(pyInterpreterServer)
    }

    fun mcpTool(pyInterpreterServer: PyInterpreterServer): MCPTool =
        MCPTool(
            type = "mcp",
            serverLabel = pyInterpreterServer.url,
            serverUrl = pyInterpreterServer.url,
            headers = mapOf("Authorization" to "Bearer ${pyInterpreterServer.apiKey}"),
        )
}

data class AuthConfig(
    val enabled: Boolean,
)

data class PlatformInfo(
    val env: String = "local",
    val appName: String = "agc-platform",
    val version: String,
    val buildTime: Instant,
    val modelSettings: ModelSettings,
    val vectorStoreInfo: VectorStoreInfo,
    val authConfig: AuthConfig,
    val pyInterpreterSettings: PyInterpreterSettings,
    val partners: Partners,
    val oAuthRedirectSpecs: OAuthRedirectSpecs,
    val agentClientSideRuntimeConfig: AgentClientSideRuntimeConfig,
) {
    companion object {
        fun publicInfo(platformInfo: PlatformInfo) =
            platformInfo.copy(
                modelSettings = ModelSettings(settingsType = platformInfo.modelSettings.settingsType, apiKey = "", model = ""),
                agentClientSideRuntimeConfig = AgentClientSideRuntimeConfig("", "", platformInfo.agentClientSideRuntimeConfig.multiPlugEnabled),
                oAuthRedirectSpecs = OAuthRedirectSpecs(),
            )
    }
}

data class AgentClientSideRuntimeConfig(
    val path: String,
    val securityKey: String,
    val multiPlugEnabled: Boolean,
)

data class OAuthRedirectSpecs(
    val agcPlatformRedirectUri: URI = URI("http://localhost:6644"),
    val agcUiHost: URI = URI("http://localhost:6645"),
)

data class VectorStoreInfo(
    val isEnabled: Boolean,
)

data class Partners(
    val details: List<Partner>,
)

data class Partner(
    val code: String,
    val name: String,
    val category: PartnerCategory,
    val enabled: Boolean,
    val deploymentLink: String,
)

enum class PartnerCategory {
    VECTOR_DB,
    EVALS,
    OBSERVABILITY,
    COMPUTE,
}
