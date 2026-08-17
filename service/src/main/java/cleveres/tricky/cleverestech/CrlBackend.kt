package cleveres.tricky.cleverestech

import androidx.annotation.VisibleForTesting
import java.io.IOException

/** Thin managed transport boundary for immutable CRL state owned by the Rust backend. */
internal object CrlBackend {
    @VisibleForTesting
    internal var refreshOverride: ((ByteArray) -> CrlWire.Handle?)? = null

    @VisibleForTesting
    internal var queryOverride: ((Long, List<CrlWire.Query>) -> CrlWire.Result?)? = null

    fun refresh(crl: ByteArray): CrlWire.Handle? {
        refreshOverride?.let { return it(crl) }
        val payloadLength = CrlWire.refreshLength(crl.size) ?: return null
        val response =
            NativeBackend.transact(
                OP_CRL,
                payloadLength,
                CrlWire.MAX_RESPONSE_BYTES,
                propagateTransportFailure = true,
            ) { output ->
                CrlWire.writeRefresh(output, crl)
            } ?: return null
        return CrlWire.decodeRefresh(response)
            ?: throw RustBackendUnavailableException(IOException("Invalid CRL refresh response"))
    }

    fun check(
        generation: Long,
        queries: List<CrlWire.Query>,
    ): CrlWire.Result? {
        queryOverride?.let { return it(generation, queries) }
        val payloadLength = CrlWire.queryLength(queries) ?: return null
        val response =
            NativeBackend.transact(
                OP_CRL,
                payloadLength,
                CrlWire.MAX_RESPONSE_BYTES,
                propagateTransportFailure = true,
            ) { output ->
                CrlWire.writeQuery(output, generation, queries)
            } ?: return null
        return CrlWire.decodeQuery(response, generation, queries.size)
            ?: throw RustBackendUnavailableException(IOException("Invalid CRL query response"))
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        refreshOverride = null
        queryOverride = null
    }

    private const val OP_CRL = 27
}
