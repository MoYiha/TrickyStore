package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack

/** Atomic publication boundary between Rust-owned secret keys and the managed selection snapshot. */
internal object KeyboxActivation {
    @Volatile
    private var committedInstanceSerial = 0L

    fun commitAndPublish(keyboxes: List<CertHack.KeyBox>): Boolean {
        if (!KeyboxLoader.commitActive(keyboxes)) {
            Logger.e("Refusing to publish keyboxes because the Rust active-set commit failed")
            val instance = NativeBackend.currentInstanceSerial()
            if (instance > 0 && !BackendStateRecovery.isRecovering()) {
                BackendStateRecovery.recover(instance)
            }
            return false
        }
        CertHack.setKeyboxes(keyboxes)
        committedInstanceSerial = NativeBackend.currentInstanceSerial()
        return true
    }

    fun invalidateBackendInstance() {
        committedInstanceSerial = 0
        CertHack.clearCertificateCache()
    }

    fun isCommittedForCurrentInstance(): Boolean {
        val current = NativeBackend.currentInstanceSerial()
        return current > 0 && committedInstanceSerial == current
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        committedInstanceSerial = 0
    }
}
