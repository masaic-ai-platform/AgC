package ai.masaic.platform.api.user

import ai.masaic.openresponses.api.exception.HttpStatusCodeException
import ai.masaic.platform.api.user.auth.google.GoogleAuthentication
import ai.masaic.platform.api.user.auth.google.GooglePreAuthToken
import ai.masaic.platform.api.user.auth.google.GoogleTokenVerifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono

@Profile("platform")
@Configuration
@EnableWebFluxSecurity
@ConditionalOnProperty(name = ["platform.deployment.auth.enabled"], havingValue = "true")
class PlatformSecurityConfig {
    @Bean
    fun filterChain(
        authConfigProperties: AuthConfigProperties,
        googleTokenVerifier: GoogleTokenVerifier,
        http: ServerHttpSecurity,
    ): SecurityWebFilterChain =
        if (authConfigProperties.enabled) {
            http
                .csrf { it.disable() }
                .cors { it.configurationSource(corsConfigurationSource()) }
                .authorizeExchange { authorizeExchange ->
                    authorizeExchange
                        .pathMatchers("/v1/dashboard/platform/info", "/v1/dashboard/platform/auth/verify", "/v1/dashboard/oauth/callback")
                        .permitAll()
                        .pathMatchers("/v1/dashboard/**")
                        .authenticated()
                        .pathMatchers("/v1/agents/**")
                        .authenticated()
                        .anyExchange()
                        .permitAll()
                }.addFilterAt(googleAuthFilter(googleTokenVerifier), SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(userSessionContextFilter(authConfigProperties), SecurityWebFiltersOrder.AUTHENTICATION)
                .build()
        } else {
            http
                .csrf { it.disable() }
                .cors { it.configurationSource(corsConfigurationSource()) }
                .authorizeExchange { authorizeExchange ->
                    authorizeExchange.anyExchange().permitAll()
                }.addFilterAfter(userSessionContextFilter(authConfigProperties), SecurityWebFiltersOrder.AUTHENTICATION)
                .build()
        }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun googleAuthenticationManager(googleTokenVerifier: GoogleTokenVerifier): ReactiveAuthenticationManager =
        ReactiveAuthenticationManager { token ->
            when (token) {
                is GooglePreAuthToken -> {
                    googleTokenVerifier
                        .verifyAsync(token.credentials)
                        .map { GoogleAuthentication(it) }
                }
                else -> Mono.empty()
            }
        }

    @Bean
    fun googleTokenVerifier(authConfigProperties: AuthConfigProperties) = GoogleTokenVerifier(authConfigProperties.google, authConfigProperties.whitelistedUsers)

    private fun googleAuthFilter(googleTokenVerifier: GoogleTokenVerifier): AuthenticationWebFilter {
        val manager =
            ReactiveAuthenticationManager { token ->
                Mono
                    .just(token)
                    .cast(GooglePreAuthToken::class.java)
                    .flatMap { googleTokenVerifier.verifyAsync(it.credentials) }
                    .map { GoogleAuthentication(it) }
            }

        val filter = AuthenticationWebFilter(manager)
        filter.setServerAuthenticationConverter { ex ->
            Mono
                .justOrEmpty(ex.request.headers.getFirst("X-Google-Token"))
                .map(::GooglePreAuthToken)
        }
        return filter
    }

    @Bean
    fun userSessionContextFilter(authConfigProperties: AuthConfigProperties): WebFilter =
        WebFilter { exchange, chain ->
            val sessionId =
                exchange.request.headers
                    .getFirst("x-session-id")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val headerUserId =
                exchange.request.headers
                    .getFirst("x-user-id")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            chain
                .filter(exchange)
                .contextWrite { ctx ->
                    var updated = ctx
                    // Always include session id when present
                    if (sessionId != null) updated = updated.put("x-session-id", sessionId)
                    // Only include x-user-id in context when auth is disabled
                    if (!authConfigProperties.enabled && headerUserId != null) {
                        updated = updated.put("x-user-id", headerUserId)
                    }
                    updated
                }
        }
}

@Profile("platform")
@Configuration
@EnableWebFluxSecurity
@ConditionalOnProperty(name = ["platform.deployment.auth.enabled"], havingValue = "false", matchIfMissing = true)
class PlatformNoOpSecurityConfig {
    @Bean
    fun filterChain(
        http: ServerHttpSecurity,
    ): SecurityWebFilterChain = http
                .csrf { it.disable() }
                .cors { it.configurationSource(corsConfigurationSource()) }
                .authorizeExchange { authorizeExchange ->
                    authorizeExchange.anyExchange().permitAll()
                }.addFilterAfter(userSessionContextFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun userSessionContextFilter(): WebFilter =
        WebFilter { exchange, chain ->
            val sessionId =
                exchange.request.headers
                    .getFirst("x-session-id")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val headerUserId =
                exchange.request.headers
                    .getFirst("x-user-id")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            chain
                .filter(exchange)
                .contextWrite { ctx ->
                    var updated = ctx
                    // Always include session id when present
                    if (sessionId != null) updated = updated.put("x-session-id", sessionId)
                    // Only include x-user-id in context when auth is disabled
                    if (headerUserId != null) {
                        updated = updated.put("x-user-id", headerUserId)
                    }
                    updated
                }
        }
}

/**
 * Google OAuth configuration
 */
@Profile("platform")
@ConfigurationProperties("platform.deployment.auth")
data class AuthConfigProperties(
    val enabled: Boolean = false,
    val google: GoogleAuthConfig = GoogleAuthConfig(),
    val whitelistedUsers: Set<String>? = null,
)

data class GoogleAuthConfig(
    val issuer: String = "https://accounts.google.com",
    val audience: String = "N/A",
    val jwksUri: String = "https://www.googleapis.com/oauth2/v3/certs",
)

data class AuthConfig(
    val enabled: Boolean,
)

class PlatformAccessForbiddenException(
    message: String,
) : HttpStatusCodeException(httpStatusCode = "403", message = message)
