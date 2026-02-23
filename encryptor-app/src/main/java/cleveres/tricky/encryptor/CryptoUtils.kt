package cleveres.tricky.encryptor

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val KEY_ALIAS = "cleveres_encryptor_signing_key"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_ALGORITHM = "AES"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ITERATION_COUNT = 250000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val CBOX_MAGIC = "CBOX"
    private const val CBOX_VERSION = 1

    fun generateSigningKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
            )
            keyPairGenerator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            keyPairGenerator.generateKeyPair()
        }
    }

    fun getPublicKeyBase64(): String? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    private fun signData(data: ByteArray): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Key not found")

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(entry.privateKey)
        signature.update(data)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    fun encryptAndWriteCbox(
        outputStream: OutputStream,
        xmlContent: String,
        author: String,
        password: String
    ) {
        // 1. Prepare Data to Sign
        val signatureInput = author + xmlContent
        val signatureBase64 = signData(signatureInput.toByteArray(StandardCharsets.UTF_8))

        // 2. Create JSON Payload
        val json = JSONObject()
        json.put("author", author)
        json.put("signature", signatureBase64)
        json.put("xml_content", xmlContent)
        val plaintext = json.toString().toByteArray(StandardCharsets.UTF_8)

        // 3. Derive Key
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        val secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, AES_ALGORITHM)

        // 4. Encrypt
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)

        // 5. Write to Stream
        // [4 bytes: ASCII "CBOX"]
        outputStream.write(CBOX_MAGIC.toByteArray(StandardCharsets.US_ASCII))
        // [4 bytes: version uint32 big-endian, value 1]
        val versionBytes = java.nio.ByteBuffer.allocate(4).putInt(CBOX_VERSION).array()
        outputStream.write(versionBytes)
        // [16 bytes: PBKDF2 salt]
        outputStream.write(salt)
        // [12 bytes: AES-GCM IV]
        outputStream.write(iv)
        // [N bytes: ciphertext + tag]
        outputStream.write(ciphertext)

        // Cleanup
        java.util.Arrays.fill(keyBytes, 0.toByte())
        // Cannot easily zero char array from String but PBEKeySpec makes a copy anyway
    }
}
