package ai.masaic.temporal

import ai.masaic.temporal.factory.TemporalWorkerFactory
import ai.masaic.temporal.service.ConfigurationHotReloadService
import ai.masaic.temporal.util.ConfigurationManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.authorization.AuthorizationGrpcMetadataProvider
import io.temporal.authorization.AuthorizationTokenSupplier
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.common.converter.*
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import org.slf4j.LoggerFactory

object TemporalWorkerMain {
    private val logger = LoggerFactory.getLogger(TemporalWorkerMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        // ---- Temporal Cloud connection (API-key + TLS) ----
        val target = System.getenv("TEMPORAL_TARGET") ?: "us-east-1.aws.api.temporal.io:7233"
        val namespace = System.getenv("TEMPORAL_NAMESPACE") ?: "local-dev.rtghm"
        val apiKey = System.getenv("TEMPORAL_API_KEY")?: error("TEMPORAL_API_KEY is not set")
        logger.info("Connecting to Temporal Cloud...")
        val tokenSupplier = AuthorizationTokenSupplier { "Bearer $apiKey" }
        val serviceOptions =
            WorkflowServiceStubsOptions
                .newBuilder()
                .setTarget(target)
                .setEnableHttps(true)
                .addGrpcMetadataProvider(AuthorizationGrpcMetadataProvider(tokenSupplier))
                .build()

        val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)
        val clientOptions =
            WorkflowClientOptions
                .newBuilder()
                .setNamespace(namespace)
                .setDataConverter(kotlinJacksonDataConverter())
                .build()
        val client = WorkflowClient.newInstance(service, clientOptions)

        // ---- Load configuration and create workers ----
        logger.info("Loading worker configuration...")
        val config = TemporalWorkerFactory.loadConfiguration()
        val workerFactory = TemporalWorkerFactory(client, config)
        val workers = workerFactory.createWorkers()
        workerFactory.startAllWorkers()

        logger.info("Temporal Workers started for profile: {}", config.profileid)
        logger.info("Active queues: {}", workers.keys.joinToString(", "))
        logger.info("Namespace: {}, Target: {}", namespace, target)

        // ---- Start hot reload service ----
        val configPath = ConfigurationManager.resolveConfigurationPath()
        val hotReloadService = ConfigurationHotReloadService(client, configPath.toString())
        hotReloadService.setCurrentWorkerFactory(workerFactory)
        hotReloadService.startWatching()
        logger.info("Configuration hot reload service started")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info("Temporal Workers: Shutting down...")
                hotReloadService.stopWatching()
                workerFactory.shutdownAllWorkers()
            },
        )
        // Keep the workers alive
        Thread.currentThread().join()
    }

    private fun kotlinObjectMapper(): ObjectMapper =
        ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun kotlinJacksonDataConverter(): DataConverter {
        val mapper = kotlinObjectMapper()
        val json = JacksonJsonPayloadConverter(mapper)
        return DefaultDataConverter(
            json,
            ByteArrayPayloadConverter(),
            ProtobufJsonPayloadConverter(),
            NullPayloadConverter()
        )
    }
}
