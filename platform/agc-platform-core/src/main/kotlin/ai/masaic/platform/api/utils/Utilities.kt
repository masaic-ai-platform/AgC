package ai.masaic.platform.api.utils

import ai.masaic.openresponses.api.service.ResponseProcessingException
import java.io.ByteArrayOutputStream
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Utilities {
    fun encryptCredentials(jsonString: String): String {
        return try {
            // Convert JSON string to bytes
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            // Create salt (8 bytes)
            val salt = ByteArray(8)
            java.security.SecureRandom().nextBytes(salt)

            // Create key and IV using PBKDF2 with empty password (as in your command)
            // Generate 384 bits (48 bytes) = 32 bytes for key + 16 bytes for IV
            val keySpec: KeySpec = PBEKeySpec("".toCharArray(), salt, 100000, 384)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyIv = keyFactory.generateSecret(keySpec).encoded

            // Split key and IV (32 bytes key + 16 bytes IV)
            val key = ByteArray(32)
            val iv = ByteArray(16)
            System.arraycopy(keyIv, 0, key, 0, 32)
            System.arraycopy(keyIv, 32, iv, 0, 16)

            val secretKey = SecretKeySpec(key, "AES")

            // Encrypt using AES-256-CBC with derived IV
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val encryptedBytes = cipher.doFinal(jsonBytes)

            // Combine "Salted__" + salt + encrypted data (OpenSSL format)
            val combined = ByteArrayOutputStream().use { baos ->
                baos.write("Salted__".toByteArray(Charsets.US_ASCII)) // OpenSSL header
                baos.write(salt)
                baos.write(encryptedBytes)
                baos.toByteArray()
            }
            // Encode to base64
            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            throw ResponseProcessingException("Failed to encrypt credentials: ${e.message}")
        }
    }
}
