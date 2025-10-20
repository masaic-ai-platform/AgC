package ai.masaic.platform.api.config

import ai.masaic.openresponses.api.model.ModelSettings
import ai.masaic.openresponses.api.model.SystemSettingsType
import ai.masaic.platform.api.repository.InMemoryUserLoginAuditRepository
import ai.masaic.platform.api.repository.MongoUserLoginAuditRepository
import ai.masaic.platform.api.repository.UserLoginAuditRepository
import ai.masaic.platform.api.security.AuthConfigProperties
import ai.masaic.platform.api.service.UserLoginAuditService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import java.net.URI

@Configuration
class AgCPlatformServerConfig {
    @Bean
    @ConditionalOnMissingBean
    fun platformInfo(
        @Value(value = "\${open-responses.store.vector.search.provider:file}") vectorSearchProviderType: String,
        buildProperties: BuildProperties,
        modelSettings: ModelSettings,
        pyInterpreterSettings: PyInterpreterSettings,
        configProperties: AuthConfigProperties,
        partners: Partners,
        @Value("\${platform.deployment.oauth.redirectAgcHost:na}") agcPlatformRedirectBaseUrl: String = "na",
        @Value("\${platform.deployment.oauth.agcUiHost:na}") agcUiHost: String = "na",
        @Value("\${platform.deployment.agc-cs-runtime.path:../agc-client-runtime/java-sdk}") agcRuntimePath: String,
        @Value("\${platform.deployment.agc-cs-runtime.securitykey:na}") securityKey: String,
        @Value("\${platform.deployment.multiplug.enabled:false}") multiPlugEnabled: Boolean,
    ): PlatformInfo {
        val vectorStoreInfo =
            if (vectorSearchProviderType == "qdrant") VectorStoreInfo(true) else VectorStoreInfo(false)

        val oAuthRedirectSpecs = if (agcPlatformRedirectBaseUrl != "na" && agcUiHost != "na") OAuthRedirectSpecs(URI(agcPlatformRedirectBaseUrl), URI(agcUiHost)) else OAuthRedirectSpecs()

        if (multiPlugEnabled && securityKey == "na") throw IllegalStateException("property platform.deployment.agc-cs-runtime.securityKey is not defined")

        return PlatformInfo(
            version = "v${buildProperties.version}",
            buildTime = buildProperties.time,
            modelSettings = ModelSettings(modelSettings.settingsType, "", ""),
            vectorStoreInfo = vectorStoreInfo,
            authConfig = AuthConfig(configProperties.enabled),
            pyInterpreterSettings =
                if (pyInterpreterSettings.systemSettingsType == SystemSettingsType.DEPLOYMENT_TIME) {
                    // to avoid api key leak
                    PyInterpreterSettings(
                        SystemSettingsType.DEPLOYMENT_TIME,
                    )
                } else {
                    PyInterpreterSettings()
                },
            partners = partners,
            oAuthRedirectSpecs = oAuthRedirectSpecs,
            agentClientSideRuntimeConfig = AgentClientSideRuntimeConfig(agcRuntimePath, securityKey),
        )
    }

    @Configuration
    @ConditionalOnProperty(name = ["platform.deployment.auth.enabled"], havingValue = "true")
    class UserLoginAuditConfiguration {
        @Bean
        @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "mongodb")
        fun mongoUserLoginAuditRepository(mongoTemplate: ReactiveMongoTemplate): UserLoginAuditRepository = MongoUserLoginAuditRepository(mongoTemplate)

        @Bean
        @ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "in-memory", matchIfMissing = true)
        fun inMemoryUserLoginAuditRepository(): UserLoginAuditRepository = InMemoryUserLoginAuditRepository()

        @Bean
        fun userLoginAuditService(userLoginAuditRepository: UserLoginAuditRepository) = UserLoginAuditService(userLoginAuditRepository)
    }
}
