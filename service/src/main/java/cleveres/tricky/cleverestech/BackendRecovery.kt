package cleveres.tricky.cleverestech

import androidx.annotation.VisibleForTesting

/** Compatibility entry point; all recovery work is owned by [BackendStateRecovery]. */
internal object BackendRecovery {
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
        // Exactly one retry. A second failure escapes; no recovery call is made from this retry.
        return operation()
    }

    fun recoverOnce(force: Boolean): Boolean {
        recoveryOverride?.let { return it() }
        val identity = NativeBackend.currentBackendIdentity() ?: return false
        // Identity changes are detected by NativeBackend's mandatory PID+epoch PING handshake. The
        // single-flight coordinator rejects an overlapping recovery without waiting, preventing
        // recovery/refresh lock inversion while the active attempt rebuilds the same identity.
        return BackendStateRecovery.recover(identity)
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        recoveryOverride = null
        BackendStateRecovery.resetForTesting()
    }
}
