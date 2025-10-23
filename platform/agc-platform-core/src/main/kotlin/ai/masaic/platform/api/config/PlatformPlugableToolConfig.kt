package ai.masaic.platform.api.config

import ai.masaic.openresponses.tool.PlugableToolAdapter
import ai.masaic.platform.api.tools.SimpleMultiPlugAdapter
import ai.masaic.platform.api.tools.TemporalConfig
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.nio.charset.StandardCharsets
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Configuration
class PlatformPlugableToolConfig {
    @Bean
    @Primary
    @ConditionalOnProperty(name = ["platform.deployment.multiplug.enabled"], havingValue = "true")
    fun simpleMultiplugAdapter(adapters: List<PlugableToolAdapter>) = SimpleMultiPlugAdapter(adapters)

    @Bean
    fun temporalConfig(platformInfo: PlatformInfo): TemporalConfig =
        if (platformInfo.agentClientSideRuntimeConfig.multiPlugEnabled) {
            val temporalConfigMap = OpenSslDecryptor.decrypt(platformInfo.agentClientSideRuntimeConfig.securityKey)
            val namespace = temporalConfigMap["namespace"] ?: throw IllegalStateException("namespace not found in security Key")
            val target = temporalConfigMap["target"] ?: throw IllegalStateException("target not found in security Key")
            val apiKey = temporalConfigMap["apiKey"] ?: throw IllegalStateException("apiKey not found in security Key")
            TemporalConfig(namespace = namespace, target = target, apiKey = apiKey)
        } else {
            TemporalConfig()
        }

    object OpenSslDecryptor {
        private const val OPENSSL_HDR = "Salted__"
        private const val SALT_LEN = 8
        private const val KEY_LEN = 32
        private const val IV_LEN = 16
        private const val ITER = 100_000

        private val mapper = ObjectMapper()

        fun decrypt(base64: String): Map<String, String> {
            val all = Base64.getDecoder().decode(base64)
            if (all.size < 16 || OPENSSL_HDR != String(all, 0, 8, StandardCharsets.US_ASCII)) {
                throw IllegalArgumentException("Not OpenSSL salted format")
            }

            val salt = all.copyOfRange(8, 16)
            val ct = all.copyOfRange(16, all.size)

            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec("".toCharArray(), salt, ITER, (KEY_LEN + IV_LEN) * 8)
            val keyIv = skf.generateSecret(spec).encoded
            val key = keyIv.copyOfRange(0, KEY_LEN)
            val iv = keyIv.copyOfRange(KEY_LEN, KEY_LEN + IV_LEN)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plain = cipher.doFinal(ct)

            return try {
                mapper.readValue(plain, object : TypeReference<Map<String, String>>() {})
            } catch (ex: Exception) {
                throw IllegalStateException(
                    """
Unable to parse security key. Encode the security string using guide: https://github.com/masaic-ai-platform/AgC/tree/main/platform/agc-client-runtime#generate-key, should follow format:
{
    "namespace": "<temporal_namespace>",
    "target": "<temporal_target>",
    "apiKey" : "<temporal_api_key>
}
                    """.trimMargin(),
                )
            }
        }
    }
}
