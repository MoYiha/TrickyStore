package cleveres.tricky.cleverestech

import androidx.annotation.VisibleForTesting
import java.io.IOException

/** Thin managed transport boundary for CRL parsing and revocation matching performed in Rust. */
internal object CrlBackend {
    @VisibleForTesting
    internal var checkerOverride: ((ByteArray, List<CrlWire.Query>) -> CrlWire.Result?)? = null

    fun check(
        crl: ByteArray,
        queries: List<CrlWire.Query>,
    ): CrlWire.Result? {
        checkerOverride?.let { return it(crl, queries) }
        val payloadLength = CrlWire.requestLength(crl.size, queries) ?: return null
        val response =
            NativeBackend.transact(
                OP_CRL_CHECK_BATCH,
                payloadLength,
                CrlWire.MAX_RESPONSE_BYTES,
                propagateTransportFailure = true,
            ) { output ->
                CrlWire.writeRequest(output, crl, queries)
            } ?: return null
        return CrlWire.decode(response, queries.size)
            ?: throw RustBackendUnavailableException(IOException("Invalid CRL backend response"))
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        checkerOverride = null
    }

    private const val OP_CRL_CHECK_BATCH = 27
}
