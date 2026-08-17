package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.NativeBackend
import java.io.IOException

/**
 * Thin CTSB compatibility boundary. Production PBKDF2/AES-GCM lives in the
 * unprivileged Rust backend; this object retains only cheap format guards and
 * the legacy public API used by the WebUI service.
 */
object BackupEncryptor {
    internal const val MAGIC = "CTSB"

    private const val LEGACY_VERSION = 1
    private const val VERSION = 2
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 16
    private const val HEADER_LENGTH = 4 + Int.SIZE_BYTES + SALT_LENGTH + IV_LENGTH
    private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
    private const val MAX_PASSWORD_CHARS = 1024

    private val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)

    fun encrypt(
        plaintext: ByteArray,
        password: String,
    ): ByteArray {
        require(plaintext.size <= MAX_BACKUP_BYTES) { "Backup exceeds $MAX_BACKUP_BYTES bytes" }
        require(password.length <= MAX_PASSWORD_CHARS) { "Backup password exceeds $MAX_PASSWORD_CHARS characters" }
        return NativeBackend.encryptBackup(plaintext, password)
            ?: throw IOException("Native CTSB encryption failed")
    }

    /** Decrypts v2 backups and retains read compatibility with CTSB v1. */
    fun decrypt(
        data: ByteArray,
        password: String,
    ): ByteArray {
        if (data.size < HEADER_LENGTH + TAG_LENGTH || data.size > MAX_BACKUP_BYTES + HEADER_LENGTH + TAG_LENGTH) {
            throw IOException("Invalid CTSB backup size")
        }
        if (!isEncryptedBackup(data)) throw IOException("Not a CTSB encrypted backup")

        val version = readIntBigEndian(data, magicBytes.size)
        if (version != LEGACY_VERSION && version != VERSION) {
            throw IOException("Unsupported CTSB version: $version")
        }
        if (password.length > MAX_PASSWORD_CHARS) {
            throw IOException("Backup password exceeds $MAX_PASSWORD_CHARS characters")
        }

        return NativeBackend.decryptBackup(data, password)
            ?: throw IOException("Encrypted CTSB backup rejected")
    }

    fun isEncryptedBackup(bytes: ByteArray): Boolean {
        if (bytes.size < magicBytes.size) return false
        for (index in magicBytes.indices) {
            if (bytes[index] != magicBytes[index]) return false
        }
        return true
    }

    private fun readIntBigEndian(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
}
