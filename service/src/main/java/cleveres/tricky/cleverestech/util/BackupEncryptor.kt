package cleveres.tricky.cleverestech.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts settings backup ZIPs using PBKDF2-derived AES-256-GCM.
 *
 * File format (CTSB — CleveresTricky Settings Backup):
 *   [4]  magic "CTSB" (ASCII)
 *   [4]  version = 1 (big-endian int)
 *   [16] PBKDF2 salt
 *   [12] AES-GCM IV
 *   [N]  AES-256-GCM ciphertext + 128-bit authentication tag
 */
object BackupEncryptor {
    internal const val MAGIC = "CTSB"
    private const val VERSION = 1
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ITERATION_COUNT = 250000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val keyBytes = deriveKey(password, salt)
        try {
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plaintext)

            val bos = ByteArrayOutputStream()
            bos.write(MAGIC.toByteArray(Charsets.US_ASCII))
            bos.write(ByteBuffer.allocate(4).putInt(VERSION).array())
            bos.write(salt)
            bos.write(iv)
            bos.write(ciphertext)
            return bos.toByteArray()
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        DataInputStream(ByteArrayInputStream(data)).use { dis ->
            val magic = ByteArray(4).also { dis.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != MAGIC) throw IOException("Not a CTSB encrypted backup")
            val versionBytes = ByteArray(4).also { dis.readFully(it) }
            val version = ByteBuffer.wrap(versionBytes).int
            if (version != VERSION) throw IOException("Unsupported CTSB version: $version")
            val salt = ByteArray(SALT_LENGTH).also { dis.readFully(it) }
            val iv = ByteArray(IV_LENGTH).also { dis.readFully(it) }
            val encryptedData = dis.readBytes()

            val keyBytes = deriveKey(password, salt)
            try {
                val secretKey = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                return cipher.doFinal(encryptedData)
            } finally {
                keyBytes.fill(0)
            }
        }
    }

    fun isEncryptedBackup(bytes: ByteArray): Boolean =
        bytes.size >= 4 && String(bytes.copyOf(4), Charsets.US_ASCII) == MAGIC

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val passwordChars = password.toCharArray()
        try {
            val spec = PBEKeySpec(passwordChars, salt, ITERATION_COUNT, KEY_LENGTH)
            return factory.generateSecret(spec).encoded
        } finally {
            passwordChars.fill('\u0000')
        }
    }
}
