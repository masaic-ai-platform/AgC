package ai.masaic.platform.api.service

import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.search.VectorStoreService
import ai.masaic.openresponses.api.service.storage.FileService
import ai.masaic.platform.api.config.PlatformCoreConfig
import ai.masaic.platform.api.config.PlatformInfo
import ai.masaic.platform.api.model.PlatformAgent
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.file.Path

@Component
@ConditionalOnProperty(name = ["platform.deployment.agent.bootstrap.enabled"], havingValue = "true", matchIfMissing = true)
class PlatformServerBootstrapService(
    private val agentBootStrapKit: AgentBootStrapKit,
    private val platformInfo: PlatformInfo,
    private val fileService: FileService,
    private val vectorStoreService: VectorStoreService,
    private val modelSettings: ModelSettings
) {
    private val log = KotlinLogging.logger { }
    private val agentDefFileName: String = "platform-bootstrap-agents.json"

    @EventListener(ApplicationReadyEvent::class)
    suspend fun bootstrap() {
        try {
            log.info("Starting platform agent bootstrapping process...")
            val agentDefJson = agentBootStrapKit.loadClasspathResource(agentDefFileName) ?: "[]"
            val agents = agentBootStrapKit.loadAgentDefinitions(agentDefJson)

            val agentsToLoad = agents.map { agent ->
                val fileSearchTool = agent.tools.firstOrNull{it.type == "file_search"}
                fileSearchTool?.let {
                    prepareFileSearchAgent(agent)
                }?: agent
            }

            if (agentsToLoad.isEmpty()) {
                log.warn("No agent definitions found in classpath resource: $agentDefFileName")
                return
            }

            agentBootStrapKit.bootstrapAgents(agentsToLoad, emptyMap())
            log.info("Agent bootstrapping completed.")
        } catch (e: Exception) {
            log.error("Failed to bootstrap agents: ${e.message}", e)
        }


        if(!platformInfo.vectorStoreInfo.isEnabled) {
            log.info { "Vector store is not enabled. Not loading file search based agent" }
            return
        }
    }

    private suspend fun prepareFileSearchAgent(agent: PlatformAgent): PlatformAgent {

        val embeddingsModel = PlatformCoreConfig.resolveModelSettings(modelSettings, "openai@text-embedding-3-small")
        val agentFile = ClassPathResource("${agent.name}-File.pdf")
        
        // Convert ClassPathResource to FilePart
        val filePart = agentFile.toFilePart()
        val file = fileService.uploadFilePart(filePart = filePart, purpose = "user_data")
//        fileService.deleteFile()
        
        val vectorStore = vectorStoreService.createVectorStore(CreateVectorStoreRequest(name = "report-analyst-store"))
        vectorStoreService.createVectorStoreFile(vectorStoreId = vectorStore.id, CreateVectorStoreFileRequest(fileId = file.id, attributes = mapOf("category" to "user_document", "language" to "en"), modelInfo = ModelInfo(model = embeddingsModel.qualifiedModelName, bearerToken = embeddingsModel.bearerToken)))
        log.info { "Preparing ${agent.name} to load." }
        return agent.copy(tools = listOf(FileSearchTool(type = "file_search", vectorStoreIds = listOf(vectorStore.id), modelInfo = null)))
    }
    
    /**
     * Extension function to convert ClassPathResource to FilePart
     */
    private fun ClassPathResource.toFilePart(): FilePart =
        object : FilePart {
            override fun name(): String = "file"
            
            override fun headers(): HttpHeaders = HttpHeaders()
            
            override fun filename(): String = this@toFilePart.filename ?: "unknown"
            
            override fun transferTo(dest: Path): Mono<Void> = 
                Mono.fromCallable { 
                    this@toFilePart.inputStream.use { input ->
                        dest.toFile().outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }.then()
            
            override fun content() =
                DataBufferUtils.readInputStream(
                    { this@toFilePart.inputStream },
                    DefaultDataBufferFactory(),
                    4096,
                )
        }
}
