package cleveres.tricky.cleverestech.keystore

import cleveres.tricky.cleverestech.ManagedOpaqueKeyOracle
import java.io.Reader

/** Java-friendly test-only facade for the opaque managed keybox compatibility oracle. */
object ManagedKeyboxStateOracle {
    @JvmStatic
    fun readFromXml(reader: Reader?) {
        ManagedOpaqueKeyOracle.readFromXml(reader)
    }

    @JvmStatic
    fun parse(
        reader: Reader,
        filename: String,
    ): List<CertHack.KeyBox> = ManagedOpaqueKeyOracle.parse(reader, filename)
}
