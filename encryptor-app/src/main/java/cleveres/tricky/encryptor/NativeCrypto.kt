package cleveres.tricky.encryptor

internal object NativeCrypto {
    const val ENCRYPT_OK = 0
    const val ENCRYPT_INVALID_INPUT = 1
    const val ENCRYPT_RANDOM_UNAVAILABLE = 2
    const val ENCRYPT_CRYPTO_FAILURE = 3
    const val ENCRYPT_STORAGE_FAILURE = 4
    const val ENCRYPT_INTERNAL_FAILURE = 5

    init {
        System.loadLibrary("cleveres_encryptor_crypto")
    }

    external fun validateKeyboxXml(xml: ByteArray): Boolean

    external fun encryptAndSave(
        noBackupDirectory: String,
        filename: String,
        authorUtf8: ByteArray,
        xmlUtf8: ByteArray,
        signatureBase64: ByteArray,
        passwordUtf16: CharArray,
    ): Int

    external fun ensureVault(noBackupDirectory: String): Boolean

    external fun storeEncrypted(
        noBackupDirectory: String,
        filename: String,
        ciphertext: ByteArray,
    ): Boolean

    external fun readEncrypted(
        noBackupDirectory: String,
        filename: String,
    ): ByteArray?

    external fun deleteEncrypted(
        noBackupDirectory: String,
        filename: String,
    ): Boolean
}
