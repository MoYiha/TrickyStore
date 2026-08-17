package cleveres.tricky.cleverestech

import androidx.annotation.VisibleForTesting

internal typealias RustBackendStateException = BackendStateException

/** Compatibility entry point; all recovery work is owned by [BackendStateRecovery]. */
internal object BackendRecovery {
    @VisibleForTesting
    internal var recoveryOverride: (() -> Boolean)? = null

    fun <T> withOneRetry(operation: () -> T): T {
        try {
            return operation()
        } catch (error: BackendStateException) {
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
        // Identity changes are already detected by NativeBackend's mandatory PING handshake. The
        // single-flight coordinator deduplicates concurrent callers for the same PID+epoch.
        return BackendStateRecovery.recover(identity)
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        recoveryOverride = null
        BackendStateRecovery.resetForTesting()
    }
}

/**
 * Older Config activation code calls this after parsing. NativeBackend now performs the restart
 * detection synchronously on every newly opened socket and does not keep a second pending-reset bit.
 */
internal fun NativeBackend.consumeBackendStateReset(): Boolean = false
