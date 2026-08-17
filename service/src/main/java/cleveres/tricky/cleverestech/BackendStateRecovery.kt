package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Rebuilds all managed views that contain process-local Rust backend handles after an epoch change. */
internal object BackendStateRecovery {
    private val recoveryLock = Any()

    @Volatile
    private var recovering = false

    @Volatile
    private var recoveredIdentity: NativeBackend.BackendIdentity? = null

    @androidx.annotation.VisibleForTesting
    internal var recoveryOverride: ((NativeBackend.BackendIdentity) -> Boolean)? = null

    fun isRecovering(): Boolean = recovering

    fun recover(expectedIdentity: NativeBackend.BackendIdentity): Boolean {
        synchronized(recoveryLock) {
            if (recovering) return false
            val current = NativeBackend.currentBackendIdentity() ?: return false
            if (current != expectedIdentity) return false
            if (recoveredIdentity == current && KeyboxActivation.isCommittedForCurrentInstance()) {
                return true
            }
            recovering = true
            return try {
                val recoveryIdentity = NativeBackend.beginBackendRecovery()
                if (recoveryIdentity != expectedIdentity) return@try false

                recoveryOverride?.let { override ->
                    return@try override(recoveryIdentity).also { success ->
                        if (success) recoveredIdentity = recoveryIdentity
                    }
                }

                KeyboxActivation.invalidateBackendInstance()
                CboxManager.invalidateBackendHandles()
                KeyboxVerifier.invalidateBackendGeneration()
                CertHack.clearCertificateCache()

                val crl = KeyboxVerifier.refreshPersistedCrlForBackendRecovery()
                if (crl == null) {
                    // Recovery never performs network work. Prune the new backend to an empty active
                    // set; managed publication remains fail-closed until bounded state can rebuild.
                    KeyboxActivation.commitAndPublish(emptyList())
                    return@try false
                }

                val success = rebuildKeyboxes(crl)
                if (success && NativeBackend.isCurrentBackendIdentity(recoveryIdentity)) {
                    recoveredIdentity = recoveryIdentity
                    true
                } else {
                    false
                }
            } catch (error: Exception) {
                Logger.e("Rust backend state recovery failed: ${error.javaClass.simpleName}")
                false
            } finally {
                recovering = false
            }
        }
    }

    private fun rebuildKeyboxes(crl: CrlWire.Handle): Boolean {
        val rebuilt = ArrayList<CertHack.KeyBox>()
        val root = Config.getConfigRoot()
        val legacy = File(root, KEYBOX_FILE)
        if (isSafeXml(legacy)) {
            rebuilt += KeyboxLoader.parseFile(KeyboxLoader.FileScope.CONFIG_ROOT, KEYBOX_FILE)
        }

        val directory = Config.keyboxDirectory
        if (Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val names = ArrayList<String>(MAX_KEYBOX_FILES)
            Files.newDirectoryStream(directory.toPath()).use { entries ->
                for (entry in entries) {
                    val name = entry.fileName.toString()
                    if (!name.endsWith(".xml", ignoreCase = true)) continue
                    require(names.size < MAX_KEYBOX_FILES) { "Too many keybox files during recovery" }
                    val file = entry.toFile()
                    if (isSafeXml(file)) names += name
                }
            }
            names.sort()
            for (name in names) {
                rebuilt += KeyboxLoader.parseFile(KeyboxLoader.FileScope.KEYBOX_DIRECTORY, name)
            }
        }

        // CBOX recovery uses only protected credential caches. The fresh CRL generation above is
        // already cached, so refresh stays local and does not perform network work.
        CboxManager.refresh()
        rebuilt += CboxManager.getUnlockedKeyboxes()

        // Re-materialize encrypted server caches against this backend instance. Scheduler startup
        // is guarded by ServerManager and does not create duplicate periodic work.
        ServerManager.initialize()
        rebuilt += ServerManager.getLoadedKeyboxes()

        val allValid = rebuilt.all { KeyboxVerifier.verifyKeybox(it, crl) == KeyboxVerifier.Status.VALID }
        val active = if (allValid) rebuilt else emptyList()
        if (!allValid) Logger.e("Recovered keybox pool contains an invalid or revoked entry; pruning active set")
        return KeyboxActivation.commitAndPublish(active)
    }

    private fun isSafeXml(file: File): Boolean =
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            file.length() in 1..MAX_KEYBOX_XML_BYTES

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        synchronized(recoveryLock) {
            recovering = false
            recoveredIdentity = null
            recoveryOverride = null
        }
    }

    private const val KEYBOX_FILE = "keybox.xml"
    private const val MAX_KEYBOX_FILES = 64
    private const val MAX_KEYBOX_XML_BYTES = 10L * 1024 * 1024
}
