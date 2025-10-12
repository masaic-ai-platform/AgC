package ai.masaic.temporal

import ai.masaic.openresponses.tool.PluggedToolRequest
import ai.masaic.temporal.factory.TemporalWorkerFactory
import ai.masaic.temporal.service.ConfigurationHotReloadService
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.temporal.authorization.AuthorizationGrpcMetadataProvider
import io.temporal.authorization.AuthorizationTokenSupplier
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.common.converter.*
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import org.slf4j.LoggerFactory
import java.time.Duration

object TemporalWorkerMain {
    private val logger = LoggerFactory.getLogger(TemporalWorkerMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        // ---- Temporal Cloud connection (API-key + TLS) ----
        val target = System.getenv("TEMPORAL_TARGET") ?: "<region>.<cloud_provider>.api.temporal.io:7233"
        val namespace = System.getenv("TEMPORAL_NAMESPACE") ?: "<namespace_id>.<account_id>"
        val apiKey = System.getenv("TEMPORAL_API_KEY") ?: error("TEMPORAL_API_KEY is not set")
        val tokenSupplier = AuthorizationTokenSupplier { "Bearer $apiKey" }
        val serviceOptions =
            WorkflowServiceStubsOptions
                .newBuilder()
                .setTarget(target)
                .setEnableHttps(true)
                .setRpcTimeout(Duration.ofSeconds(30))
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
        val hotReloadService = ConfigurationHotReloadService(client)
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
}

class ClientSideToolExecutionActivityImpl : ClientSideToolExecutionActivity {
    private val mapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(ClientSideToolExecutionActivityImpl::class.java)

    override fun executeTool(pluggedToolRequest: PluggedToolRequest): String {
        logger.info("###### Place holder for custom implementing at client side ############ ")
        logger.debug("Executing tool with arguments: {}", pluggedToolRequest.arguments)
        val result = "Executed Successfully"
        return "$result"
    }
}

// Build one mapper and reuse everywhere
fun kotlinObjectMapper(): ObjectMapper =
    ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun kotlinJacksonDataConverter(): DataConverter {
    val mapper = kotlinObjectMapper()
    // Use Jackson for JSON payloads; include others if you need them
    val json = JacksonJsonPayloadConverter(mapper)
    return DefaultDataConverter(json, ByteArrayPayloadConverter(), ProtobufJsonPayloadConverter(), NullPayloadConverter())
}
