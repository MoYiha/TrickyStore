package cleveres.tricky.cleverestech.util

import java.nio.charset.StandardCharsets

/** Classifies CBOX envelopes before they cross into the unprivileged Rust backend. */
object CboxDecryptor {
    private const val CBOX_MAGIC = "CBOX"
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16
    private const val HEADER_BYTES = 4 + Int.SIZE_BYTES + SALT_LENGTH + IV_LENGTH + GCM_TAG_LENGTH

    private val magicBytes = CBOX_MAGIC.toByteArray(StandardCharsets.US_ASCII)

    fun hasSupportedEnvelopeHeader(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_BYTES) return false
        for (index in magicBytes.indices) {
            if (bytes[index] != magicBytes[index]) return false
        }
        val versionOffset = magicBytes.size
        val version =
            ((bytes[versionOffset].toInt() and 0xff) shl 24) or
                ((bytes[versionOffset + 1].toInt() and 0xff) shl 16) or
                ((bytes[versionOffset + 2].toInt() and 0xff) shl 8) or
                (bytes[versionOffset + 3].toInt() and 0xff)
        return version in 1..2
    }
}
