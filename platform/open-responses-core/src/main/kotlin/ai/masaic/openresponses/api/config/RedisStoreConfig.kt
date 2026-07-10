package ai.masaic.openresponses.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("open-responses.store.redis")
data class RedisStoreConfig(
    val keyPrefix: String = "agc",
    val ttlMinutes: Long = 60,
)
