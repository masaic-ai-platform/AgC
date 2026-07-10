package ai.masaic

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication

class OpenResponsesApplicationTest {
    @Test
    fun `open responses server excludes Redisson auto configuration`() {
        val annotation = OpenResponsesApplication::class.java.getAnnotation(SpringBootApplication::class.java)

        assertThat(annotation.excludeName)
            .contains("org.redisson.spring.starter.RedissonAutoConfigurationV2")
    }
}
