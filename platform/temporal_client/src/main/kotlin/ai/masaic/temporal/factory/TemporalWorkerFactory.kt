package ai.masaic.temporal.factory

import ai.masaic.temporal.ClientSideToolExecutionActivityImpl
import ai.masaic.temporal.config.QueueConfig
import ai.masaic.temporal.config.WorkerConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.client.WorkflowClient
import io.temporal.worker.Worker
import io.temporal.worker.WorkerFactory
import org.slf4j.LoggerFactory
import java.io.InputStream

class TemporalWorkerFactory(
    private val workflowClient: WorkflowClient,
    private val config: WorkerConfiguration,
) {
    private val logger = LoggerFactory.getLogger(TemporalWorkerFactory::class.java)
    private val workerFactory = WorkerFactory.newInstance(workflowClient)
    private val workers = mutableMapOf<String, Worker>()

    fun createWorkers(): Map<String, Worker> {
        logger.info("Creating workers for profile: {}", config.profileid)
        logger.info("Number of queues: {}", config.queues.size)

        config.queues.forEach { queueConfig ->
            val worker = createWorkerForQueue(queueConfig)
            val fullQueueName = "${config.profileid}.${queueConfig.name}"
            workers[fullQueueName] = worker
            logger.info("Created worker for queue: {}", fullQueueName)
        }

        return workers.toMap()
    }

    private fun createWorkerForQueue(queueConfig: QueueConfig): Worker {
        val fullQueueName = "${config.profileid}.${queueConfig.name}"
        val worker = workerFactory.newWorker(fullQueueName)
        
        // Register default activities for all workers
        worker.registerActivitiesImplementations(ClientSideToolExecutionActivityImpl())
        logger.debug("Registered ClientSideToolExecutionActivity for queue: {}", fullQueueName)
        
        return worker
    }

    fun startAllWorkers() {
        logger.info("Starting all workers...")
        workerFactory.start()
        logger.info("All workers started successfully")
    }

    fun shutdownAllWorkers() {
        logger.info("Shutting down all workers...")
        workerFactory.shutdown()
        logger.info("All workers shut down")
    }

    fun getWorker(queueName: String): Worker? = workers[queueName]

    fun getAllWorkers(): Map<String, Worker> = workers.toMap()

    companion object {
        fun loadConfiguration(configPath: String = "/worker-config.json"): WorkerConfiguration {
            val inputStream: InputStream = 
                TemporalWorkerFactory::class.java.getResourceAsStream(configPath)
                    ?: throw IllegalArgumentException("Configuration file not found: $configPath")
            
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            return objectMapper.readValue(
                inputStream,
                WorkerConfiguration::class.java,
            )
        }
    }
}
