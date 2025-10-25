package common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Map;

public class Util {
    private static final String OPENSSL_HDR = "Salted__";
    private static final int SALT_LEN = 8, KEY_LEN = 32, IV_LEN = 16, ITER = 100000;

    private static Map<String, String> credentials = null;

    /**
     * Decrypts the base64 encoded credentials and caches them
     *
     * @param base64 The base64 encoded credentials
     * @return Map containing decrypted credentials
     * @throws Exception if decryption fails
     */
    public static Map<String, String> decrypt(String base64) throws Exception {
        if (credentials == null) {
            credentials = performDecrypt(base64);
        }
        return credentials;
    }

    /**
     * Gets a specific credential value by name
     *
     * @param credName The name of the credential to retrieve
     * @return The credential value or null if not found
     * @throws Exception if credentials are not loaded
     */
    public static String getCreds(String credName) throws Exception {
        if (credentials == null) {
            throw new IllegalStateException("Credentials not initialized. Call Util.decrypt() first.");
        }
        return credentials.get(credName);
    }

    /**
     * Performs the actual decryption of the base64 encoded credentials
     *
     * @param base64 The base64 encoded credentials
     * @return Map containing decrypted credentials
     * @throws Exception if decryption fails
     */
    private static Map<String, String> performDecrypt(String base64) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64);
        if (all.length < 16 || !OPENSSL_HDR.equals(new String(all, 0, 8, StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Not OpenSSL salted format");
        byte[] salt = new byte[SALT_LEN];
        System.arraycopy(all, 8, salt, 0, SALT_LEN);
        byte[] ct = new byte[all.length - 16];
        System.arraycopy(all, 16, ct, 0, ct.length);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(("").toCharArray(), salt, ITER, (KEY_LEN + IV_LEN) * 8);
        byte[] keyIv = skf.generateSecret(spec).getEncoded();
        byte[] key = new byte[KEY_LEN], iv = new byte[IV_LEN];
        System.arraycopy(keyIv, 0, key, 0, KEY_LEN);
        System.arraycopy(keyIv, KEY_LEN, iv, 0, IV_LEN);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] plain = c.doFinal(ct);
        return new ObjectMapper().readValue(plain, new TypeReference<Map<String, String>>() {
        });
    }
}
