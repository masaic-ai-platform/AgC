package ai.masaic.platform.usecases.api.service

// import com.netflix.conductor.client.http.ConductorClientRequest
// import com.netflix.conductor.client.http.WorkflowClient
// import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest
// import com.netflix.conductor.common.run.Workflow
// import com.netflix.conductor.common.run.Workflow.WorkflowStatus
// import com.netflix.conductor.sdk.workflow.task.InputParam
// import com.netflix.conductor.sdk.workflow.task.WorkerTask
// import io.orkes.conductor.client.ApiClient
// import io.orkes.conductor.client.OrkesClients
// import io.orkes.conductor.client.http.OrkesWorkflowClient
import mu.KotlinLogging
import org.springframework.stereotype.Component

// class AtomWorkflowService(val workflowClient: OrkesWorkflowClient) {
@Component
class AtomWorkflowService {
    private val log = KotlinLogging.logger { }

    companion object {
        const val ATOM_WF_NAME = "atomAgentWorkflow"
    }

//    suspend fun runWorkflow(input: AtomWorkflowInput, callback: (lastStatus: String?, currentStatus: String) -> Unit): AtomWorkflowOutput {
//        log.info { "Starting with input=$input" }
//        val request = StartWorkflowRequest()
//        request.name = ATOM_WF_NAME
//        request.input = mapOf("atomAgentInput" to input.inputPayload)
//        request.taskToDomain = mapOf("atomAgent" to "default")
//        val wfId = workflowClient.startWorkflow(request)
//        var currentStatus = "STARTED"
//        callback(null, currentStatus)
//        var lastStatus = currentStatus
//        currentStatus = WorkflowStatus.RUNNING.name
//        var result = "no result available."
//        while (currentStatus == WorkflowStatus.RUNNING.name) {
//            val workflow = workflowClient.getWorkflow(wfId, true)
//            lastStatus = currentStatus
//            currentStatus = workflow.status.name
//            callback(lastStatus, currentStatus)
//            delay(2*1000)
//            result = when(currentStatus) {
//                WorkflowStatus.COMPLETED.name -> {
//                    val workflowOutput = workflow.output["atomAgentOutput"]
//                    workflowOutput?.let { workflowOutput as String } ?: result
//                }
//                else -> "Workflow run $currentStatus, not output available."
//            }
//        }
//        log.info { "result = $result" }
//        return AtomWorkflowOutput(result)
//    }
}

class AtomWorkflowTaskWorker {
    //    @WorkerTask(value = "atomAgent", domain = "default")
//    fun executeTask(@InputParam("atomAgentInput") atomAgentInput: String?= null): Map<String, String> {
//        runBlocking { delay(20*1000) }
//        return mapOf("atomAgentOutput" to "The test task completed successfully for input=$atomAgentInput")
//    }
}
