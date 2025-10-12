package ai.masaic.temporal.util

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object ConfigurationManager {
    private val logger = LoggerFactory.getLogger(ConfigurationManager::class.java)
    private const val CONFIG_FILE_NAME = "worker-config.json"
    private const val CONFIG_DIR = "config"
    
    fun resolveConfigurationPath(): Path {
        return if (isRunningFromJar()) {
            getJarConfigPath()
        } else {
            getDevConfigPath()
        }
    }
    
    private fun isRunningFromJar(): Boolean {
        val location = javaClass.protectionDomain.codeSource?.location?.path ?: return false
        return location.endsWith(".jar")
    }
    
    private fun getDevConfigPath(): Path {
        val devPath = Paths.get("platform/temporal_client/src/main/resources/$CONFIG_FILE_NAME")
        logger.info("Running in development mode, using: {}", devPath.toAbsolutePath())
        return devPath
    }
    
    private fun getJarConfigPath(): Path {
        val configFile = Paths.get(CONFIG_DIR, CONFIG_FILE_NAME)
        
        if (!Files.exists(configFile)) {
            Files.createDirectories(configFile.parent)
            
            javaClass.getResourceAsStream("/$CONFIG_FILE_NAME")?.use { inputStream ->
                Files.copy(inputStream, configFile, StandardCopyOption.REPLACE_EXISTING)
                logger.info("Extracted configuration from JAR to: {}", configFile.toAbsolutePath())
            } ?: throw IllegalStateException("Failed to load $CONFIG_FILE_NAME from JAR resources")
        } else {
            logger.info("Using existing configuration: {}", configFile.toAbsolutePath())
        }
        
        return configFile
    }
}
