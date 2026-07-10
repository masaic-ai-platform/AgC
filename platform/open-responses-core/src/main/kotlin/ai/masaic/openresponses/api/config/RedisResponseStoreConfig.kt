package ai.masaic.openresponses.api.config

import ai.masaic.openresponses.api.client.RedisCompletionStore
import ai.masaic.openresponses.api.client.RedisResponseStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.redisson.api.RedissonReactiveClient
import org.redisson.spring.starter.RedissonAutoConfigurationV2
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ConditionalOnProperty(name = ["open-responses.store.type"], havingValue = "redis")
@EnableConfigurationProperties(RedisStoreConfig::class)
@Import(RedissonAutoConfigurationV2::class)
class RedisResponseStoreConfig {
    @Bean
    fun redisResponseStore(
        redissonClient: RedissonReactiveClient,
        objectMapper: ObjectMapper,
        redisStoreConfig: RedisStoreConfig,
    ) = RedisResponseStore(redissonClient, objectMapper, redisStoreConfig)

    @Bean
    fun redisCompletionStore(
        redissonClient: RedissonReactiveClient,
        objectMapper: ObjectMapper,
        redisStoreConfig: RedisStoreConfig,
    ) = RedisCompletionStore(redissonClient, objectMapper, redisStoreConfig)
}
