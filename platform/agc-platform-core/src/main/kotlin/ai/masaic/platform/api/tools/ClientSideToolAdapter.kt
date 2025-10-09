package ai.masaic.platform.api.tools

import ai.masaic.openresponses.tool.PlugableToolAdapter
import ai.masaic.openresponses.tool.PluggedToolRequest
import io.temporal.activity.ActivityInterface
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.common.RetryOptions
import io.temporal.spring.boot.ActivityImpl
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import java.util.*

@Component
@ConditionalOnBean(SimpleMultiPlugAdapter::class)
class ClientSideToolAdapter(
    private val temporalToolExecutor: TemporalToolExecutor,
) : PlugableToolAdapter {
    override suspend fun callTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String? = temporalToolExecutor.executeTool(pluggedToolRequest, eventEmitter)
}

@Component
@ConditionalOnBean(SimpleMultiPlugAdapter::class)
class TemporalToolExecutor(
    private val workflowClient: WorkflowClient,
) {
    private val log = KotlinLogging.logger { }

    suspend fun executeTool(
        pluggedToolRequest: PluggedToolRequest,
        eventEmitter: (ServerSentEvent<String>) -> Unit,
    ): String? {
        return try {
            // Generate unique workflow ID
            val workflowId = "cs-tool-${pluggedToolRequest.loopContextInfo.loopId}-${UUID.randomUUID().toString().take(4)}"

            // Create workflow options
            val options =
                WorkflowOptions
                    .newBuilder()
                    .setTaskQueue(pluggedToolRequest.name)
                    .setWorkflowId(workflowId)
                    .setWorkflowRunTimeout(java.time.Duration.ofSeconds(5 * 60))
                    .build()

            // Create workflow stub
            val workflow =
                workflowClient
                    .newWorkflowStub(ClientSideToolExecutionWorkflow::class.java, options)

            val result = workflow.execute(pluggedToolRequest)

            return result
        } catch (e: Exception) {
            log.error(e) { "Error executing AtomAgentWorkflow" }
            "Error executing AtomAgentWorkflow, error = ${e.message}"
        }
    }
}

@WorkflowInterface
interface ClientSideToolExecutionWorkflow {
    @WorkflowMethod
    fun execute(pluggedToolRequest: PluggedToolRequest): String
}

@ActivityInterface
interface ClientSideToolExecutionActivity {
    fun executeTool(pluggedToolRequest: PluggedToolRequest): String
}

@Component
@ActivityImpl(taskQueues = ["\${spring.temporal.workers.task-queue}"])
class ClientSideToolExecutionActivityImpl : ClientSideToolExecutionActivity {
    override fun executeTool(pluggedToolRequest: PluggedToolRequest): String {
        println("${pluggedToolRequest.loopContextInfo.loopId} executing tool ${pluggedToolRequest.name} with arguments=${pluggedToolRequest.arguments}")
        return "Tool executed successfully..... because of security reasons, can't share more details"
    }
}

@WorkflowImpl(taskQueues = ["\${spring.temporal.workers.task-queue}"])
class ClientSideToolExecutionWorkflowImpl : ClientSideToolExecutionWorkflow {
    private val activity =
        io.temporal.workflow.Workflow
            .newActivityStub(
                ClientSideToolExecutionActivity::class.java,
                io.temporal.activity.ActivityOptions
                    .newBuilder()
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .setStartToCloseTimeout(java.time.Duration.ofSeconds(4 * 60))
                    .build(),
            )

    override fun execute(pluggedToolRequest: PluggedToolRequest) = activity.executeTool(pluggedToolRequest)
}
