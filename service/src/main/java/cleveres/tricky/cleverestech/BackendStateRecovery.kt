package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import java.util.concurrent.locks.ReentrantLock

/** Rebuilds all managed views that contain process-local Rust backend handles after an epoch change. */
internal object BackendStateRecovery {
    private val recoveryLock = ReentrantLock()

    @Volatile
    private var recovering = false

    @Volatile
    private var recoveredIdentity: NativeBackend.BackendIdentity? = null

    @androidx.annotation.VisibleForTesting
    internal var recoveryOverride: ((NativeBackend.BackendIdentity) -> Boolean)? = null

    fun isRecovering(): Boolean = recovering

    fun recover(expectedIdentity: NativeBackend.BackendIdentity): Boolean {
        // Recovery can need the process-wide refresh coordinator. Never wait here: a normal
        // refresh may already own that coordinator and be unwinding a backend-state failure.
        // Blocking on another recovery in that state would invert recovery -> refresh against
        // refresh -> recovery and deadlock both workers. The active single flight or a later
        // backend operation will complete/retry recovery.
        if (!recoveryLock.tryLock()) return false
        return try {
            if (recovering) return false
            val current = NativeBackend.currentBackendIdentity() ?: return false
            if (current != expectedIdentity) return false
            if (recoveredIdentity == current) return true
            recovering = true
            return try {
                val override = recoveryOverride
                if (override != null) {
                    override(current).also { success ->
                        if (success) recoveredIdentity = current
                    }
                } else {
                    recoverBackendState(expectedIdentity)
                }
            } catch (error: Exception) {
                Logger.e("Rust backend state recovery failed: ${error.javaClass.simpleName}")
                false
            } finally {
                recovering = false
            }
        } finally {
            recoveryLock.unlock()
        }
    }

    private fun recoverBackendState(expectedIdentity: NativeBackend.BackendIdentity): Boolean {
        val recoveryIdentity = NativeBackend.beginBackendRecovery()
        if (recoveryIdentity != expectedIdentity) return false

        KeyboxActivation.invalidateBackendInstance()
        CboxManager.invalidateBackendHandles()
        KeyboxVerifier.invalidateBackendGeneration()
        CertHack.clearCertificateCache()

        val crl = KeyboxVerifier.refreshPersistedCrlForBackendRecovery()
        if (crl == null) {
            // Recovery never performs network work. Prune the new backend to an empty active
            // set; managed publication remains fail-closed until bounded state can rebuild.
            KeyboxActivation.commitAndPublish(emptyList())
            return false
        }

        // Reopen protected CBOX credentials and encrypted server caches against this process.
        // fetchCrl() observes the fresh in-memory generation above, so this stays local.
        CboxManager.refresh()
        ServerManager.initialize()

        // Config clears its XML opaque-handle caches, reparses configured files in Rust,
        // validates against the recovered CRL generation, commits the active set, and only
        // then publishes the managed CertHack snapshot. Recovery is disabled inside this
        // call so failure cannot recurse into another recovery loop.
        val success = Config.rebuildBackendKeyboxesAfterRestart(crl)
        if (!success || !NativeBackend.isCurrentBackendIdentity(recoveryIdentity)) return false

        recoveredIdentity = recoveryIdentity
        return true
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        recoveryLock.lock()
        try {
            recovering = false
            recoveredIdentity = null
            recoveryOverride = null
        } finally {
            recoveryLock.unlock()
        }
    }
}
