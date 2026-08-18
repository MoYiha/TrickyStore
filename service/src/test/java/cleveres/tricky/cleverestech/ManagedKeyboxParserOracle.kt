package cleveres.tricky.cleverestech

import java.io.File
import java.io.StringReader

/** JVM-only legacy oracle for tests whose production path requires the supervised Rust backend. */
internal object ManagedKeyboxParserOracle {
    fun install() {
        ManagedOpaqueKeyOracle.reset()
        KeyboxLoader.parserOverride = { bytes, filename ->
            ManagedOpaqueKeyOracle.parse(StringReader(bytes.toString(Charsets.UTF_8)), filename)
        }
        KeyboxLoader.fileParserOverride = { scope, filename ->
            val file =
                when (scope) {
                    KeyboxLoader.FileScope.CONFIG_ROOT -> File(Config.getConfigRoot(), filename)
                    KeyboxLoader.FileScope.KEYBOX_DIRECTORY -> File(File(Config.getConfigRoot(), "keyboxes"), filename)
                }
            file.bufferedReader(Charsets.UTF_8).use { ManagedOpaqueKeyOracle.parse(it, filename) }
        }
        KeyboxLoader.activeSetOverride = { keyIds -> keyIds.all(ManagedOpaqueKeyOracle::contains) }
    }

    fun reset() {
        KeyboxLoader.resetForTesting()
        ManagedOpaqueKeyOracle.reset()
    }
}
