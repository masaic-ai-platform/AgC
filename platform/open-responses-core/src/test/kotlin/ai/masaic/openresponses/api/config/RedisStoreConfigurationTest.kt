package ai.masaic.openresponses.api.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class RedisStoreConfigurationTest {
    @Test
    fun `redis store configuration is inactive by default`() {
        ApplicationContextRunner()
            .withUserConfiguration(RedisResponseStoreConfig::class.java)
            .withPropertyValues("open-responses.store.type=in-memory")
            .run { context ->
                assertThat(context).doesNotHaveBean("redisResponseStore")
                assertThat(context).doesNotHaveBean("redisCompletionStore")
            }
    }
}
