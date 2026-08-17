package cleveres.tricky.cleverestech

import androidx.annotation.VisibleForTesting
import cleveres.tricky.cleverestech.keystore.CertHack
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Transient transport or protocol failure at the unprivileged Rust backend boundary. */
internal class RustBackendUnavailableException(
    cause: Throwable? = null,
) : IOException("Rust backend is unavailable", cause)

/**
 * One managed boundary for keybox material. XML is parsed and private keys are normalized by the
 * unprivileged Rust backend; this layer only materializes Android/JCA provider objects.
 *
 * Ownership of [xml] transfers to [parse] and its contents are wiped before return. Filesystem-backed
 * XML is opened descriptor-relatively by the privileged Rust broker and parsed only after the file
 * descriptor reaches the unprivileged Rust backend.
 */
internal object KeyboxLoader {
    internal enum class FileScope(
        val wireValue: Int,
    ) {
        CONFIG_ROOT(0),
        KEYBOX_DIRECTORY(1),
    }

    private val fileBackendOutageObserved = AtomicBoolean(false)

    @VisibleForTesting
    internal var parserOverride: ((ByteArray, String) -> List<CertHack.KeyBox>)? = null

    @VisibleForTesting
    internal var fileParserOverride: ((FileScope, String) -> List<CertHack.KeyBox>)? = null

    fun parse(
        xml: ByteArray,
        filename: String,
    ): List<CertHack.KeyBox> =
        try {
            val override = parserOverride
            if (override != null) {
                override(xml, filename)
            } else {
                val document = NativeBackend.parseKeybox(xml)
                if (document == null) emptyList() else KeyboxJcaAdapter.materialize(document, filename)
            }
        } finally {
            xml.fill(0)
        }

    fun parseFile(
        scope: FileScope,
        filename: String,
    ): List<CertHack.KeyBox> =
        try {
            val override = fileParserOverride
            if (override != null) {
                override(scope, filename)
            } else {
                val document = NativeBackend.parseKeyboxFile(scope.wireValue, filename)
                if (document == null) emptyList() else KeyboxJcaAdapter.materialize(document, filename)
            }
        } catch (error: RustBackendUnavailableException) {
            fileBackendOutageObserved.set(true)
            throw error
        }

    internal fun consumeFileBackendOutage(): Boolean = fileBackendOutageObserved.getAndSet(false)

    @VisibleForTesting
    internal fun resetForTesting() {
        parserOverride = null
        fileParserOverride = null
        fileBackendOutageObserved.set(false)
    }
}
