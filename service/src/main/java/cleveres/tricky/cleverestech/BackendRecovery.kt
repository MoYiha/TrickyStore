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
        return if (force || NativeBackend.consumeBackendStateReset()) {
            BackendStateRecovery.recover(identity)
        } else {
            BackendStateRecovery.recover(identity)
        }
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        recoveryOverride = null
        BackendStateRecovery.resetForTesting()
    }
}
