package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier

/** Rebuilds all managed views that contain process-local Rust backend handles after an epoch change. */
internal object BackendStateRecovery {
    private val recoveryLock = Any()

    @Volatile
    private var recovering = false

    @Volatile
    private var recoveredInstanceSerial = 0L

    @androidx.annotation.VisibleForTesting
    internal var recoveryOverride: ((Long) -> Boolean)? = null

    fun isRecovering(): Boolean = recovering

    fun recover(instanceSerial: Long): Boolean {
        if (instanceSerial <= 0 || NativeBackend.currentInstanceSerial() != instanceSerial) return false
        synchronized(recoveryLock) {
            if (NativeBackend.currentInstanceSerial() != instanceSerial) return false
            if (recovering) return false
            if (recoveredInstanceSerial == instanceSerial && KeyboxActivation.isCommittedForCurrentInstance()) {
                return true
            }
            recovering = true
            return try {
                recoveryOverride?.let { override ->
                    return@try override(instanceSerial).also { success ->
                        if (success) recoveredInstanceSerial = instanceSerial
                    }
                }

                KeyboxActivation.invalidateBackendInstance()
                Config.invalidateBackendKeyboxHandles()
                CboxManager.invalidateBackendHandles()
                KeyboxVerifier.invalidateBackendGeneration()
                CertHack.clearCertificateCache()

                val crl = KeyboxVerifier.refreshPersistedCrlForBackendRecovery()
                if (crl == null) {
                    // No bounded revocation source is available. Prune the new backend to an empty
                    // active set and keep certificate rewriting fail-closed rather than performing
                    // network work from a recovery path.
                    KeyboxActivation.commitAndPublish(emptyList())
                    return@try false
                }

                // Reopen protected CBOX credential caches against the new backend instance. The CRL
                // handle above is already cached, so refresh does not need network access.
                CboxManager.refresh()

                // Reload encrypted remote-server caches. initialize() is idempotent with respect to
                // scheduler startup and re-materializes their opaque key IDs against this instance.
                ServerManager.initialize()

                // Force configured XML files through the Rust parser again, validate against the
                // recovered Rust CRL generation, commitActive(), then publish the managed snapshot.
                Config.updateKeyBoxesSync(crl) { keybox, _ ->
                    KeyboxVerifier.verifyKeybox(keybox, crl)
                }
                val success = KeyboxActivation.isCommittedForCurrentInstance()
                if (success) recoveredInstanceSerial = instanceSerial
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
            recoveredInstanceSerial = 0
            recoveryOverride = null
        }
    }
}
