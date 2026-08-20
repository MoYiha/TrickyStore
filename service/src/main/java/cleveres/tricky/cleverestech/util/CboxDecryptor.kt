package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CboxWireLimits
import java.nio.charset.StandardCharsets

/** Classifies CBOX envelopes before they cross into the unprivileged Rust backend. */
object CboxDecryptor {
    private const val CBOX_MAGIC = "CBOX"
    private val magicBytes = CBOX_MAGIC.toByteArray(StandardCharsets.US_ASCII)

    fun hasSupportedEnvelopeHeader(bytes: ByteArray): Boolean {
        if (bytes.size < CboxWireLimits.MIN_BYTES || bytes.size > CboxWireLimits.MAX_BYTES) return false
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
