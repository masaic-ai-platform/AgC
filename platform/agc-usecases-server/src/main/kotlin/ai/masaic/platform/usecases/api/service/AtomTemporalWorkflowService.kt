package ai.masaic.platform.usecases.api.service

import io.temporal.activity.ActivityInterface
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.common.RetryOptions
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AtomTemporalWorkflowService(
    private val workflowClient: WorkflowClient,
) {
    @Value("\${spring.temporal.workers.task-queue}")
    private lateinit var taskQueue: String

    private val log = KotlinLogging.logger { }

    suspend fun runWorkflow(
        input: AtomWorkflowInput,
    ): AtomWorkflowOutput {
        return try {
            // Generate unique workflow ID
            val workflowId = "atomAgentWorkflow-${UUID.randomUUID()}"

            // Create workflow options
            val options =
                WorkflowOptions
                    .newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId(workflowId)
                    .setWorkflowRunTimeout(java.time.Duration.ofSeconds(5 * 60))
                    .build()

            // Create workflow stub
            val workflow =
                workflowClient
                    .newWorkflowStub(AtomAgentWorkflow::class.java, options)

            // Execute workflow synchronously
            val result =
                workflow
                    .execute(input.inputPayload)

            return AtomWorkflowOutput(result)
        } catch (e: Exception) {
            log.error(e) { "Error executing AtomAgentWorkflow" }
            AtomWorkflowOutput("Error executing AtomAgentWorkflow, error = ${e.message}")
        }
    }
}

@WorkflowInterface
interface AtomAgentWorkflow {
    @WorkflowMethod
    fun execute(input: String): String
}

@ActivityInterface
interface AtomAgentActivity {
    fun runAtomAgent(input: String): String
}

// @Component
// @ActivityImpl(taskQueues = ["ATOM_AGENT_Q"])
// class AtomAgentActivityImpl : AtomAgentActivity {
//
//    override fun logCallAndOpportunity(input: String): String {
//        println("completing...... logCallAndOpportunity")
//        // Simulate logging call and opportunity in Salesforce
//        return "The call log added in sales force, opportunity too. There is one activity also logged"
//    }
// }

@WorkflowImpl(taskQueues = ["\${spring.temporal.workers.task-queue}"])
class AtomAgentWorkflowImpl : AtomAgentWorkflow {
    private val activity =
        io.temporal.workflow.Workflow
            .newActivityStub(
                AtomAgentActivity::class.java,
                io.temporal.activity.ActivityOptions
                    .newBuilder()
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .setStartToCloseTimeout(java.time.Duration.ofSeconds(4 * 60))
                    .build(),
            )

    override fun execute(input: String): String = activity.runAtomAgent(input)
}

data class AtomWorkflowInput(
    val inputPayload: String,
)

data class AtomWorkflowOutput(
    val outputPayload: String,
)
