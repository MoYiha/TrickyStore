package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier

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
                Config.invalidateBackendKeyboxHandles()
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

                // The fresh Rust CRL generation is cached before CBOX/server materialization, so
                // these recovery steps remain local and bounded rather than recursively fetching.
                CboxManager.refresh()
                ServerManager.initialize()

                // XML caches were invalidated above, forcing configured files back through Rust.
                Config.updateKeyBoxesSync(crl) { keybox, _ ->
                    KeyboxVerifier.verifyKeybox(keybox, crl)
                }
                val success =
                    NativeBackend.isCurrentBackendIdentity(recoveryIdentity) &&
                        KeyboxActivation.isCommittedForCurrentInstance()
                if (success) recoveredIdentity = recoveryIdentity
                success
            } catch (error: Exception) {
                Logger.e("Rust backend state recovery failed: ${error.javaClass.simpleName}")
                false
            } finally {
                recovering = false
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        synchronized(recoveryLock) {
            recovering = false
            recoveredIdentity = null
            recoveryOverride = null
        }
    }
}
