package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.PluggedToolRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.temporal.authorization.AuthorizationGrpcMetadataProvider
import io.temporal.authorization.AuthorizationTokenSupplier
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.Worker
import io.temporal.worker.WorkerFactory
import java.util.concurrent.TimeUnit

object ActivityWorkerMain {
    @JvmStatic
    fun main(args: Array<String>) {
        // ---- Temporal Cloud connection (API-key + TLS) ----
        val target = System.getenv("PLATFORM_DEPLOYMENT_TEMPORAL_TARGET")
        val namespace = System.getenv("PLATFORM_DEPLOYMENT_TEMPORAL_NAMESPACE")
        val apiKey = System.getenv("PLATFORM_DEPLOYMENT_TEMPORAL_API-KEY")

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

        // ---- Worker hosting the ACTIVITY ONLY ----
        val factory = WorkerFactory.newInstance(client)
        val actWorker: Worker = factory.newWorker("jb.add_two_numbers")
        actWorker.registerActivitiesImplementations(ClientSideToolExecutionActivityImpl())
        factory.start()

        println("App B: Activity worker started on jb.add_two_numbers  (namespace=$namespace, target=$target)")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("App B: Shutting down activity worker...")
                factory.shutdown()
                factory.awaitTermination(10, TimeUnit.SECONDS)
            },
        )

        // Keep the activity worker alive
        Thread.currentThread().join()
    }
}

class ClientSideToolExecutionActivityImpl : ClientSideToolExecutionActivity {
    private val mapper = jacksonObjectMapper()

    override fun executeTool(pluggedToolRequest: PluggedToolRequest): String {
        val args = mapper.readValue<Args>(pluggedToolRequest.arguments)
        val result = args.a + args.b
        println("Addition result of ${args.a} + ${args.b} = $result")
        return "$result"
    }

    data class Args(
        val a: Int,
        val b: Int,
    )
}
