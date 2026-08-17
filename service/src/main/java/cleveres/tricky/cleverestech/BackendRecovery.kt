package cleveres.tricky.cleverestech

import androidx.annotation.VisibleForTesting
import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Coordinates one bounded rebuild when the unprivileged backend process identity changes. */
internal object BackendRecovery {
    private val recoveryLock = ReentrantLock()

    @Volatile
    private var lastRecoveredIdentity: NativeBackend.BackendIdentity? = null

    @VisibleForTesting
    internal var recoveryOverride: (() -> Boolean)? = null

    fun <T> withOneRetry(operation: () -> T): T {
        try {
            return operation()
        } catch (error: RustBackendStateException) {
            if (!recoverOnce(force = true)) throw error
        } catch (error: RustBackendUnavailableException) {
            if (!recoverOnce(force = false)) throw error
        }
        // Exactly one retry. A second failure escapes; there is no recursive recovery loop.
        return operation()
    }

    fun recoverOnce(force: Boolean): Boolean =
        recoveryLock.withLock {
            recoveryOverride?.let { return@withLock it() }
            try {
                val identity = NativeBackend.beginBackendRecovery()
                if (!force && lastRecoveredIdentity == identity) return@withLock true

                // All managed opaque IDs from the prior process are stale at this point.
                CertHack.setKeyboxes(emptyList())
                KeyboxVerifier.invalidateBackendGeneration()
                CboxManager.invalidateBackendHandles()

                // Recovery is deliberately offline/bounded: repopulate CRL state only from the raw
                // persisted cache. Network refresh remains the normal background path.
                val crl = KeyboxVerifier.refreshPersistedCrlForBackendRecovery()
                    ?: return@withLock failClosedEmptyStore()

                // These routines consume only protected local caches during recovery because the CRL
                // generation above is now the fresh in-memory generation returned by fetchCrl().
                CboxManager.refresh()
                ServerManager.initialize()

                if (!Config.rebuildBackendKeyboxesAfterRestart(crl)) {
                    return@withLock failClosedEmptyStore()
                }
                lastRecoveredIdentity = identity
                true
            } catch (error: Exception) {
                Logger.e("Rust backend state recovery failed", error)
                failClosedEmptyStore()
            }
        }

    private fun failClosedEmptyStore(): Boolean {
        runCatching { KeyboxLoader.commitActive(emptyList()) }
        CertHack.setKeyboxes(emptyList())
        return false
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        lastRecoveredIdentity = null
        recoveryOverride = null
    }
}
