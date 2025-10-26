package ai.masaic.platform.api.tools.oauth

import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.api.utils.AgCLoopContext
import ai.masaic.openresponses.tool.mcp.McpUnAuthorizedException
import ai.masaic.platform.api.user.UserInfoProvider
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

@Service
class MCPOAuthServiceImpl(
    private val mcpAuthTokenRepository: McpAuthTokenRepository,
    private val mcpAuthFlowMetaInfoRepository: McpAuthFlowMetaInfoRepository,
) : MCPOAuthService {
    private val log = KotlinLogging.logger {}
    private val http = WebClient.builder().build()

    override suspend fun beginOAuthFlow(
        mcpTool: MCPTool,
        redirectUri: URI,
    ): URI {
        val mcpHost = URI(mcpTool.serverUrl)
        val oAuthFlowMetaInfo = fetchOAuthFlowMetaInfo(mcpHost)
        val dynamicClientId = performDCR(oAuthFlowMetaInfo, redirectUri)

        val (verifier, challenge) = generatePkce()
        val state = randomState()
        mcpAuthFlowMetaInfoRepository.save(state, AuthFlowMetaInfo(verifier, mcpHost.toString(), oAuthFlowMetaInfo, mcpTool, redirectUri, dynamicClientId, UserInfoProvider.userId()))
        val authUri =
            URI.create(
                oAuthFlowMetaInfo.authorizationEndpoint +
                    "?response_type=code" +
                    "&client_id=" + encode(dynamicClientId) +
                    "&redirect_uri=" + encode(redirectUri.toString()) +
                    "&code_challenge=" + encode(challenge) +
                    "&code_challenge_method=S256" +
                    "&state=" + encode(state),
            )
        log.debug { "final oauth URI = $authUri" }
        return authUri
    }

    override suspend fun handleCallback(
        code: String,
        state: String,
    ): MCPTool {
        log.debug { "callback handler, received: code = $code and state = $state" }
        val rec = mcpAuthFlowMetaInfoRepository.consume(state) ?: throw IllegalStateException("invalid_state")
        val ctx = AgCLoopContext(userId = rec.userId)
        val token =
            withContext(ctx) {
                val form =
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "redirect_uri" to rec.redirectUri.toURL().toString(),
                        "code_verifier" to rec.codeVerifier,
                        // public client (no secret) per instruction
                        "client_id" to (rec.dynamicClientId),
                    )

                log.debug { "token form url encoded request=$form" }
                try {
                    val tokens =
                        http
                            .post()
                            .uri(rec.oauthFlowMetaInfo.tokenEndpoint)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .bodyValue(form.entries.joinToString("&") { (k, v) -> "$k=" + encode(v) })
                            .retrieve()
                            .bodyToMono(Map::class.java)
                            .map { body ->
                                val access = body["access_token"].toString()
                                val refresh = (body["refresh_token"] as? String)
                                val expiresIn = (body["expires_in"] as? Number)?.toLong() ?: 3600L
                                TokenSet(
                                    access,
                                    refresh,
                                    Instant.now().plusSeconds(expiresIn),
                                    McpTokenServerMetaInfo(rec.oauthFlowMetaInfo.tokenEndpoint, rec.dynamicClientId),
                                )
                            }.awaitSingle()
                    mcpAuthTokenRepository.put(rec.mcpTool.toMCPServerInfo(), tokens)
                    tokens
                } catch (e: WebClientResponseException) {
                    val errorMessage =
                        "Exception while getting tokens: statsuCode=${e.statusCode}, response=${e.responseBodyAsString}"
                    if (e.statusCode.value() == 401) {
                        throw McpUnAuthorizedException(errorMessage)
                    }
                    log.error { errorMessage }
                    throw e
                }
            }
        log.debug { "Tokens=$token" }
        return rec.mcpTool.copy(headers = mapOf("accessToken" to token.accessToken))
    }

    override suspend fun ensureFreshAccessToken(mcpTool: MCPTool): String {
        val tokens = mcpAuthTokenRepository.get(mcpTool.toMCPServerInfo()) ?: throw McpUnAuthorizedException("no_token available")

        if (tokens.expiresAt.isAfter(Instant.now().plusSeconds(5 * 60))) return tokens.accessToken

        val refresh = tokens.refreshToken ?: throw McpUnAuthorizedException("no_refresh_token available")

        val form =
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refresh,
                "client_id" to (tokens.tokenServer.clientId),
            )

        log.debug { "refresh token request=$form" }
        val accessToken =
            try {
                val updated =
                    http
                        .post()
                        .uri(tokens.tokenServer.tokenEndpoint)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .bodyValue(form.entries.joinToString("&") { (k, v) -> "$k=" + encode(v) })
                        .retrieve()
                        .bodyToMono(Map::class.java)
                        .map { body ->
                            val access = body["access_token"].toString()
                            val newRefresh = (body["refresh_token"] as? String) ?: refresh
                            val expiresIn = (body["expires_in"] as? Number)?.toLong() ?: 3600L
                            TokenSet(access, newRefresh, Instant.now().plusSeconds(expiresIn), McpTokenServerMetaInfo(tokens.tokenServer.tokenEndpoint, tokens.tokenServer.clientId))
                        }.awaitSingle()
                mcpAuthTokenRepository.put(mcpTool.toMCPServerInfo(), updated)
                updated.accessToken
            } catch (e: WebClientResponseException) {
                val errorMessage = "Exception while getting tokens: statsuCode=${e.statusCode}, response=${e.responseBodyAsString}"
                if (e.statusCode.value() == 401) {
                    throw McpUnAuthorizedException(errorMessage)
                }
                log.error { errorMessage }
                throw e
            }

        log.debug { "accessToken=$accessToken" }
        return accessToken
    }

    private suspend fun fetchOAuthFlowMetaInfo(mcpHost: URI): OAuthFlowMetaInfo {
        val prMeta =
            fetchProtectedResourceMetadata(mcpHost)
                ?: error("Unable to discover protected resource metadata for $mcpHost")

        val issuerStr =
            prMeta.authorizationServers.firstOrNull()
                ?: error("Protected resource metadata did not advertise any authorization_servers")

        val issuer = URI(issuerStr)
        val asMeta =
            fetchOAuthAuthorizationServerMetadata(issuer)
                ?: error("Unable to discover OAuth AS metadata for issuer=$issuerStr")
        val pkce = asMeta.codeChallengeMethodsSupported?.map(String::uppercase).orEmpty()
        require("S256" in pkce) { "Authorization server must support PKCE S256" }
        val oAuthFlowMetaInfo = asMeta.copy(issuer = asMeta.issuer.ifBlank { issuerStr })
        log.debug { "Oauth meta info = $oAuthFlowMetaInfo" }
        return oAuthFlowMetaInfo
    }

    private suspend fun fetchProtectedResourceMetadata(mcpHost: URI): ProtectedResourceMetadata? {
        val hostBase = URI(mcpHost.scheme, mcpHost.authority, null, null, null).toString().trimEnd('/')
        val mcpPath = (mcpHost.path ?: "").trim('/')

        // Try path-scoped first (if present), then root
        val candidates =
            buildList {
                if (mcpPath.isNotEmpty()) add("$hostBase/.well-known/oauth-protected-resource/$mcpPath")
                add("$hostBase/.well-known/oauth-protected-resource")
            }

        for (url in candidates) {
            runCatching {
                http
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(ProtectedResourceMetadata::class.java)
                    .awaitSingle()
            }.onSuccess { resource ->
                log.info { "For $url, Discovered oauth endpoint=$resource" }
                return resource
            }
        }
        return null
    }

    private suspend fun fetchOAuthAuthorizationServerMetadata(issuer: URI): OAuthFlowMetaInfo? {
        val base = URI(issuer.scheme, issuer.authority, null, null, null).toString().trimEnd('/')
        val path = (issuer.path ?: "").trim('/')

        val candidates =
            if (path.isNotEmpty()) {
                listOf("$base/.well-known/oauth-authorization-server/$path")
            } else {
                listOf("$base/.well-known/oauth-authorization-server")
            }

        for (url in candidates) {
            runCatching {
                http
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(OAuthFlowMetaInfo::class.java)
                    .awaitSingle()
            }.onSuccess { meta -> return meta }
        }
        return null
    }

    private suspend fun performDCR(
        meta: OAuthFlowMetaInfo,
        redirectUri: URI,
    ): String {
        val registration = meta.registrationEndpoint ?: throw IllegalStateException("no_registration_endpoint")
        val body =
            mapOf(
                "application_type" to "web",
                "redirect_uris" to listOf(redirectUri.toURL().toString()),
                "grant_types" to listOf("authorization_code", "refresh_token"),
                "token_endpoint_auth_method" to "none",
            )
        val clientId =
            http
                .post()
                .uri(registration)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .map { resp ->
                    val clientId = resp["client_id"].toString()
                    clientId
                }.awaitSingle()

        log.debug { "dynamic client id = $clientId" }
        return clientId
    }

    fun generatePkce(): Pair<String, String> {
        val rnd = SecureRandom()
        val bytes = ByteArray(32) // 32 random bytes → 43-char base64url string
        rnd.nextBytes(bytes)

        val verifier =
            Base64
                .getUrlEncoder() // URL-safe, no '+' or '/'
                .withoutPadding()
                .encodeToString(bytes)

        val sha256 =
            MessageDigest
                .getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))

        val challenge =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(sha256)

        return verifier to challenge
    }

    private fun sha256Url(verifier: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        return String(Base64.getEncoder().encode(digest), Charsets.UTF_8).replace("=", "")
    }

    private fun randomState(): String = generatePkce().first

    private fun encode(v: String): String = java.net.URLEncoder.encode(v, Charsets.UTF_8)
}
