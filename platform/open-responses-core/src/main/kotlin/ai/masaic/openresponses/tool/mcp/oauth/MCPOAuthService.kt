package ai.masaic.openresponses.tool.mcp.oauth

import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.tool.mcp.MCPServerInfo
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI
import java.time.Instant

data class TokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant,
    val tokenServer: McpTokenServerMetaInfo,
)

data class McpTokenServerMetaInfo(
    val tokenEndpoint: String,
    val clientId: String,
)

data class AuthFlowMetaInfo(
    val codeVerifier: String,
    val host: String,
    val oAuthFlowMetaInfo: OAuthFlowMetaInfo,
    val mcpTool: MCPTool,
    val redirectUri: URI,
    val dynamicClientId: String,
    val createdAt: Instant,
)

data class OAuthFlowMetaInfo(
    val issuer: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("registration_endpoint")
    val registrationEndpoint: String?,
    @JsonProperty("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>?,
)

data class ProtectedResourceMetadata(
    @JsonProperty("authorization_servers") val authorizationServers: List<String> = emptyList(),
)

interface McpAuthTokenRepository {
    fun put(
        mcpServerInfo: MCPServerInfo,
        tokens: TokenSet,
    )

    fun get(mcpServerInfo: MCPServerInfo): TokenSet?
}

interface McpAuthFlowMetaInfoRepository {
    fun save(
        state: String,
        rec: AuthFlowMetaInfo,
    )

    fun consume(state: String): AuthFlowMetaInfo?
}

interface MCPOAuthService {
    suspend fun beginOAuthFlow(
        mcpTool: MCPTool,
        redirectUri: URI,
    ): URI

    suspend fun handleCallback(
        code: String,
        state: String,
    ): MCPTool

    suspend fun ensureFreshAccessToken(mcpTool: MCPTool): String
}
