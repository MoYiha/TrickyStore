package cleveres.tricky.encryptor

internal object NativeCrypto {
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
    ): Boolean

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
