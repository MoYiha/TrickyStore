package cleveres.tricky.cleverestech

import java.security.PrivateKey

/**
 * Non-secret compatibility object for managed APIs that still model an issuer as a [PrivateKey].
 * The encoded bytes are an opaque backend identifier, never PKCS#8 key material.
 */
internal class BackendKeyHandle(
    algorithm: String,
    keyId: ByteArray,
) : PrivateKey {
    private val normalizedAlgorithm =
        when {
            algorithm.equals("EC", ignoreCase = true) || algorithm.equals("ECDSA", ignoreCase = true) -> "EC"
            algorithm.equals("RSA", ignoreCase = true) -> "RSA"
            else -> throw IllegalArgumentException("Unsupported backend key algorithm")
        }
    private val id = keyId.copyOf().also { require(it.size == KEY_ID_BYTES && it.any { byte -> byte != 0.toByte() }) }
    private val backendIdentity = NativeBackend.currentBackendIdentity()

    override fun getAlgorithm(): String = normalizedAlgorithm

    override fun getFormat(): String = FORMAT

    override fun getEncoded(): ByteArray {
        requireCurrentBackend()
        return id.copyOf()
    }

    internal fun keyId(): ByteArray {
        requireCurrentBackend()
        return id.copyOf()
    }

    private fun requireCurrentBackend() {
        val captured = backendIdentity ?: return
        if (!NativeBackend.isCurrentBackendIdentity(captured)) {
            throw RustBackendStateException(BackendStatus.STATE_RESET)
        }
    }

    private companion object {
        const val KEY_ID_BYTES = 16
        const val FORMAT = "CleveresTricky-KeyId-v1"
    }
}
