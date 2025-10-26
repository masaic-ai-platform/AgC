package ai.masaic

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import reactor.core.publisher.Hooks

/**
 * Main application class for the Agc platform Spring Boot application.
 *
 * This class serves as the entry point for the application and is annotated with
 * [SpringBootApplication] to enable Spring Boot's autoconfiguration.
 * We explicitly exclude MongoDB and Redis auto-configurations to prevent Spring from automatically
 * connecting to these services when they're not explicitly enabled via properties.
 * Redisson will only be configured when open-responses.tool.store.type=redis.
 */
@SpringBootApplication(
    excludeName = [
        "org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration",
        "org.redisson.spring.starter.RedissonAutoConfigurationV2",
    ],
)
@ConfigurationPropertiesScan
@EnableScheduling
class AgCPlatformServer

/**
 * Application entry point.
 *
 * @param args Command line arguments passed to the application
 */
fun main(args: Array<String>) {
    Hooks.enableAutomaticContextPropagation()
    runApplication<AgCPlatformServer>(*args)
}
