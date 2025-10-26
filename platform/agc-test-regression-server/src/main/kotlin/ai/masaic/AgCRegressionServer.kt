package ai.masaic

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import reactor.core.publisher.Hooks

/**
 * Main application class for the AgC Regression Spring Boot application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class AgCRegressionServer

/**
 * Application entry point.
 *
 * @param args Command line arguments passed to the application
 */
fun main(args: Array<String>) {
    Hooks.enableAutomaticContextPropagation()
    runApplication<AgCRegressionServer>(*args)
}
