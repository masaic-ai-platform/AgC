package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.PlugableToolAdapter
import ai.masaic.openresponses.tool.PlugableToolAdapter.Companion.isEnabled
import ai.masaic.openresponses.tool.PlugableToolDefinition
import ai.masaic.openresponses.tool.PluggedToolRequest
import ai.masaic.platform.api.user.UserInfoProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityOptions
import io.temporal.authorization.AuthorizationGrpcMetadataProvider
import io.temporal.authorization.AuthorizationTokenSupplier
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowOptions
import io.temporal.common.RetryOptions
import io.temporal.common.converter.*
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.Worker
import io.temporal.worker.WorkerFactory
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import jakarta.annotation.PreDestroy
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnBean(SimpleMultiPlugAdapter::class)
class ClientSideToolAdapter(
    private val temporalToolExecutor: TemporalToolExecutor,
    private val pluggedToolsRegistry: PluggedToolsRegistry,
) : PlugableToolAdapter {
    override suspend fun plugIn(name: String): PlugableToolDefinition? =
        if (isEnabled()) {
            pluggedToolsRegistry.get(name)
        } else {
            null
        }

    override suspend fun callTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String? {
        val updatedPluggedToolRequest = pluggedToolRequest.copy(loopContextInfo = UserInfoProvider.toLoopContextInfo(pluggedToolRequest.loopContextInfo.loopId))
        val clientSideTool = pluggedToolsRegistry.get(updatedPluggedToolRequest.name) ?: throw MultiPlugAdapterException("tool ${updatedPluggedToolRequest.name} is not available in registry. Can not execute tool")
        return when (clientSideTool) {
            is ClientSideTool -> temporalToolExecutor.executeTool(updatedPluggedToolRequest, clientSideTool, eventEmitter)
            else -> throw MultiPlugAdapterException("Available definition of ${updatedPluggedToolRequest.name} is corrupt. Can't proceed")
        }
    }
}

@Component
@ConditionalOnBean(ClientSideToolAdapter::class)
class TemporalToolExecutor(
    private val temporalConfig: TemporalConfig,
) {
    private val log = KotlinLogging.logger { }
    private lateinit var workflowClient: WorkflowClient
    private val workflowFactories = mutableMapOf<String, WorkerFactory>()

    init {
        val tokenSupplier = AuthorizationTokenSupplier { "Bearer ${temporalConfig.apiKey}" }
        val serviceOptions =
            WorkflowServiceStubsOptions
                .newBuilder()
                .setTarget(temporalConfig.target)
                .setEnableHttps(true)
                .addGrpcMetadataProvider(AuthorizationGrpcMetadataProvider(tokenSupplier))
                .build()

        val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)
        val clientOptions =
            WorkflowClientOptions
                .newBuilder()
                .setNamespace(temporalConfig.namespace)
                .setDataConverter(kotlinJacksonDataConverter())
                .build()
        workflowClient = WorkflowClient.newInstance(service, clientOptions)
        log.info { "workflow client initialised" }
    }

    suspend fun executeTool(
        pluggedToolRequest: PluggedToolRequest,
        clientSideTool: ClientSideTool,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String? {
        return try {
            createWorkerConditionally(clientSideTool)

            val workflowId = pluggedToolRequest.loopContextInfo.loopId?.let { "cs-tool-${pluggedToolRequest.loopContextInfo.loopId}-${UUID.randomUUID().toString().take(4)}" } ?: "cs-tool-${UUID.randomUUID()}"
            val options =
                WorkflowOptions
                    .newBuilder()
                    .setTaskQueue(clientSideTool.id)
                    .setWorkflowId(workflowId)
                    .setWorkflowRunTimeout(Duration.ofSeconds(30))
                    .build()

            log.debug { "Starting workflow: $workflowId for tool: ${clientSideTool.id}" }
            val startTool = workflowClient.newWorkflowStub(ClientSideToolExecutionWorkflow::class.java, options)
            val result = startTool.execute(pluggedToolRequest, clientSideTool)
            log.debug { "Result for tool: ${clientSideTool.id}=> $result" }
            return result
        } catch (e: Exception) {
            log.error(e) { "Error executing ClientSideToolExecutionWorkflow" }
            "Error executing ClientSideToolExecutionWorkflow, error = ${e.message}"
        }
    }

    private fun createWorkerConditionally(clientSideTool: ClientSideTool) {
        if (workflowFactories[clientSideTool.id] == null) {
            log.info { "creating workflow factory for tool: ${clientSideTool.id}" }
            val factory = WorkerFactory.newInstance(workflowClient)
            val wfWorker: Worker = factory.newWorker(clientSideTool.id)
            wfWorker.registerWorkflowImplementationTypes(ClientSideToolExecutionWorkflowImpl::class.java)
            factory.start()
            workflowFactories[clientSideTool.id] = factory
        }
    }

    @PreDestroy
    fun cleaUp() {
        workflowFactories.forEach { (id, factory) ->
            log.info { "shutting down workflow factory for tool: $id" }
            factory.shutdown()
            factory.awaitTermination(2, TimeUnit.SECONDS)
        }
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

@ConfigurationProperties("platform.deployment.temporal")
data class TemporalConfig(
    val target: String,
    val namespace: String,
    val apiKey: String,
)

@ActivityInterface
interface ClientSideToolExecutionActivity {
    fun executeTool(pluggedToolRequest: PluggedToolRequest): String
}

@WorkflowInterface
interface ClientSideToolExecutionWorkflow {
    @WorkflowMethod
    fun execute(
        pluggedToolRequest: PluggedToolRequest,
        clientSideTool: ClientSideTool,
    ): String
}

class ClientSideToolExecutionWorkflowImpl : ClientSideToolExecutionWorkflow {
    override fun execute(
        pluggedToolRequest: PluggedToolRequest,
        clientSideTool: ClientSideTool,
    ): String {
        val activity =
            Workflow.newActivityStub(
                ClientSideToolExecutionActivity::class.java,
                ActivityOptions
                    .newBuilder()
                    .setTaskQueue(clientSideTool.id)
                    .setStartToCloseTimeout(Duration.ofMillis(clientSideTool.executionAttributes.waitTimeInMillis))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(clientSideTool.executionAttributes.maxRetryAttempts).build())
                    .build(),
            )
        return activity.executeTool(pluggedToolRequest)
    }
}
