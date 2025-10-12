package ai.masaic.temporal.config

data class WorkerConfiguration(
    val profileid: String,
    val queues: List<QueueConfig>,
)

data class QueueConfig(
    val name: String,
)
