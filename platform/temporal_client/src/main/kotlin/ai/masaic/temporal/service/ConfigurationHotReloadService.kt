package ai.masaic.temporal.service

import ai.masaic.temporal.factory.TemporalWorkerFactory
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import io.temporal.client.WorkflowClient
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConfigurationHotReloadService(
    private val workflowClient: WorkflowClient,
    private val configPath: String = "worker-config.json",
) {
    private val logger = LoggerFactory.getLogger(ConfigurationHotReloadService::class.java)
    private val executor = Executors.newSingleThreadExecutor { r ->
        val thread = Thread(r, "config-watcher")
        thread.isDaemon = true
        thread
    }
    private var watcher: DirectoryWatcher? = null
    private var isWatching = false
    private var currentWorkerFactory: TemporalWorkerFactory? = null

    fun startWatching() {
        if (isWatching) {
            logger.warn("Configuration watcher is already running")
            return
        }

        try {
            val configFile = Paths.get(configPath)
            val configDir = configFile.parent ?: Paths.get(".")
            
            logger.info("Starting configuration hot reload watcher for: {}", configFile.toAbsolutePath())

            watcher = DirectoryWatcher.builder()
                .path(configDir)
                .listener { event ->
                    if (event.path().fileName.toString() == configFile.fileName.toString()) {
                        handleConfigChange(event)
                    }
                }
                .build()

            CompletableFuture.runAsync(
                {
                    try {
                        watcher?.watch()
                    } catch (e: Exception) {
                        logger.error("Error watching configuration file", e)
                    }
                },
                executor,
            )

            isWatching = true
            logger.info("Configuration hot reload watcher started successfully")
        } catch (e: Exception) {
            logger.error("Failed to start configuration watcher", e)
        }
    }

    fun stopWatching() {
        if (!isWatching) {
            return
        }

        try {
            watcher?.close()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            isWatching = false
            logger.info("Configuration hot reload watcher stopped")
        } catch (e: Exception) {
            logger.error("Error stopping configuration watcher", e)
        }
    }

    private fun handleConfigChange(event: DirectoryChangeEvent) {
        when (event.eventType()) {
            DirectoryChangeEvent.EventType.MODIFY -> {
                logger.info("Configuration file modified, reloading...")
                reloadConfiguration()
            }
            DirectoryChangeEvent.EventType.CREATE -> {
                logger.info("Configuration file created, reloading...")
                reloadConfiguration()
            }
            else -> {
                logger.debug("Configuration file event: {}", event.eventType())
            }
        }
    }

    private fun reloadConfiguration() {
        try {
            val configFile = Paths.get(configPath)
            if (!Files.exists(configFile)) {
                logger.warn("Configuration file does not exist: {}", configFile)
                return
            }

            logger.info("Reloading configuration from: {}", configFile)
            
            // Load new configuration
            val newConfig = TemporalWorkerFactory.loadConfiguration("/$configPath")
            
            // Shutdown existing workers
            logger.info("Shutting down existing workers for hot reload...")
            currentWorkerFactory?.shutdownAllWorkers()
            
            // Create new workers with updated configuration
            logger.info("Creating new workers with updated configuration...")
            val newWorkerFactory = TemporalWorkerFactory(workflowClient, newConfig)
            val workers = newWorkerFactory.createWorkers()
            newWorkerFactory.startAllWorkers()
            
            // Update current factory reference
            currentWorkerFactory = newWorkerFactory
            
            logger.info("Configuration hot reload completed successfully")
            logger.info("Active queues after reload: {}", workers.keys.joinToString(", "))
        } catch (e: Exception) {
            logger.error("Failed to reload configuration", e)
            logger.error("Workers may be in an inconsistent state. Manual restart may be required.")
        }
    }

    fun setCurrentWorkerFactory(workerFactory: TemporalWorkerFactory) {
        currentWorkerFactory = workerFactory
    }

    fun isWatching(): Boolean = isWatching
}
