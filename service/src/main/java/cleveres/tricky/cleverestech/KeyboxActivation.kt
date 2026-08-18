package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack

/** Atomic publication boundary between Rust-owned secret keys and the managed selection snapshot. */
internal object KeyboxActivation {
    @Volatile
    private var committedIdentity: NativeBackend.BackendIdentity? = null

    fun commitAndPublish(keyboxes: List<CertHack.KeyBox>): Boolean {
        if (!KeyboxLoader.commitActive(keyboxes)) {
            Logger.e("Refusing to publish keyboxes because the Rust active-set commit failed")
            val identity = NativeBackend.currentBackendIdentity()
            if (identity != null && !BackendStateRecovery.isRecovering()) {
                BackendStateRecovery.recover(identity)
            }
            return false
        }
        CertHack.setKeyboxes(keyboxes)
        committedIdentity = NativeBackend.currentBackendIdentity()
        return true
    }

    fun invalidateBackendInstance() {
        committedIdentity = null
        CertHack.clearCertificateCache()
    }

    fun isCommittedForCurrentInstance(): Boolean {
        val committed = committedIdentity ?: return false
        return NativeBackend.isCurrentBackendIdentity(committed)
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        committedIdentity = null
    }
}
