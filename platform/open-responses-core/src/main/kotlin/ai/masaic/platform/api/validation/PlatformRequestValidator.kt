package ai.masaic.platform.api.validation

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.model.*
import ai.masaic.openresponses.api.service.search.VectorStoreService
import ai.masaic.openresponses.api.validation.RequestValidator
import ai.masaic.platform.api.config.PlatformInfo
import ai.masaic.platform.api.config.SystemSettingsType

class PlatformRequestValidator(
    private val vectorStoreService: VectorStoreService,
    private val responseStore: ResponseStore,
    private val platformInfo: PlatformInfo,
) : RequestValidator(vectorStoreService, responseStore) {
    override suspend fun validateTool(tool: Tool) {
        when (tool) {
            is ImageGenerationTool -> {
                if (imageBaseUrl.isNullOrBlank()) {
                    if (!modelPattern.matches(tool.model)) {
                        throw IllegalArgumentException("Image model must be in provider@model format")
                    }
                }
                if (imageApiKey.isNullOrBlank() && tool.modelProviderKey.isNullOrBlank()) {
                    throw IllegalArgumentException("model_provider_key is required for image generation")
                }
            }
            is FileSearchTool -> {
                tool.vectorStoreIds?.forEach { id ->
                    ensureVectorStoreExists(id)
                }
            }
            is AgenticSeachTool -> {
                tool.vectorStoreIds?.forEach { id ->
                    ensureVectorStoreExists(id)
                }
            }
            is PyFunTool -> {
                if (platformInfo.pyInterpreterSettings.systemSettingsType == SystemSettingsType.RUNTIME && tool.interpreterServer == null) {
                    throw IllegalArgumentException("${tool.platformToolName()} requires ${tool.interpreterServerName()} fields name, url and apiKey in the request.")
                }
            }
        }
    }
}
