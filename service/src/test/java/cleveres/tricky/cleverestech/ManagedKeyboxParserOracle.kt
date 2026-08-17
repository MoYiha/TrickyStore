package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import java.io.StringReader

/** JVM-only legacy oracle for tests whose production path requires the supervised Rust backend. */
internal object ManagedKeyboxParserOracle {
    fun install() {
        KeyboxLoader.parserOverride = { bytes, filename ->
            CertHack.parseKeyboxXml(StringReader(bytes.toString(Charsets.UTF_8)), filename)
        }
    }

    fun reset() {
        KeyboxLoader.parserOverride = null
    }
}
