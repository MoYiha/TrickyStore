package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CrlBackend
import cleveres.tricky.cleverestech.CrlWire
import cleveres.tricky.cleverestech.keystore.CertHack
import java.security.cert.X509Certificate

/** Immutable handle to CRL state parsed exactly once by the unprivileged Rust backend. */
internal class CrlSnapshot private constructor(
    private val handle: CrlWire.Handle,
) {
    val rawEntryCount: Int get() = handle.rawEntryCount
    val normalizedEntryCount: Int get() = handle.normalizedEntryCount

    fun verify(keyboxes: List<CertHack.KeyBox>): List<KeyboxVerifier.Status>? =
        try {
            verifyChecked(keyboxes)
        } catch (_: Exception) {
            null
        }

    private fun verifyChecked(keyboxes: List<CertHack.KeyBox>): List<KeyboxVerifier.Status>? {
        if (keyboxes.isEmpty()) return emptyList()

        val starts = IntArray(keyboxes.size) { -1 }
        val ends = IntArray(keyboxes.size) { -1 }
        val invalid = BooleanArray(keyboxes.size)
        val queries = ArrayList<CrlWire.Query>()

        for (index in keyboxes.indices) {
            val chain = keyboxes[index].certificates()
            if (chain.isEmpty()) {
                invalid[index] = true
                continue
            }
            starts[index] = queries.size
            for (certificate in chain) {
                if (certificate !is X509Certificate) {
                    invalid[index] = true
                    continue
                }
                if (queries.size >= CrlWire.MAX_QUERY_COUNT) return null
                val serial = certificate.serialNumber.toByteArray()
                val spki = certificate.publicKey.encoded ?: return null
                if (serial.size !in 1..CrlWire.MAX_SERIAL_BYTES ||
                    spki.size !in 1..CrlWire.MAX_SPKI_BYTES
                ) {
                    return null
                }
                queries += CrlWire.Query(serial, spki)
            }
            ends[index] = queries.size
        }

        if (queries.isEmpty()) {
            return keyboxes.indices.map { index ->
                if (invalid[index]) KeyboxVerifier.Status.INVALID else KeyboxVerifier.Status.VALID
            }
        }

        val result = CrlBackend.check(handle.generation, queries) ?: return null
        if (result.generation != handle.generation || result.revoked.size != queries.size) return null

        return keyboxes.indices.map { index ->
            when {
                invalid[index] -> KeyboxVerifier.Status.INVALID
                isAnyRevoked(result.revoked, starts[index], ends[index]) -> KeyboxVerifier.Status.REVOKED
                else -> KeyboxVerifier.Status.VALID
            }
        }
    }

    private fun isAnyRevoked(
        revoked: BooleanArray,
        start: Int,
        end: Int,
    ): Boolean {
        if (start < 0 || end < start) return false
        for (index in start until end) {
            if (revoked[index]) return true
        }
        return false
    }

    companion object {
        fun parse(raw: ByteArray): CrlSnapshot? {
            if (raw.size !in 1..CrlWire.MAX_CRL_BYTES) return null
            val handle = CrlBackend.refresh(raw) ?: return null
            return CrlSnapshot(handle)
        }
    }
}
