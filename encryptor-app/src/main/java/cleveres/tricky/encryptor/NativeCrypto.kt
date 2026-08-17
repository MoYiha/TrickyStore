package cleveres.tricky.encryptor

internal object NativeCrypto {
    init {
        System.loadLibrary("cleveres_encryptor_crypto")
    }

    external fun encryptAndSave(
        noBackupDirectory: String,
        filename: String,
        authorUtf8: ByteArray,
        xmlUtf8: ByteArray,
        signatureBase64: ByteArray,
        passwordUtf16: CharArray,
    ): Boolean
}
