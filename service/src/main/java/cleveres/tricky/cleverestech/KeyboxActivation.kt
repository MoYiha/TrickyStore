package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack

/** Atomic process-wide publication boundary between Rust secret keys and managed selection state. */
internal object KeyboxActivation {
    internal data class RefreshTicket internal constructor(val generation: Long)

    internal enum class PublicationResult {
        COMMITTED,
        SUPERSEDED,
        FAILED,
    }

    private val publicationLock = Any()
    private var refreshGeneration = 0L

    @Volatile
    private var committedIdentity: NativeBackend.BackendIdentity? = null

    /**
     * Starts a new logical refresh. Generation assignment shares the publication lock so a refresh
     * cannot become newer halfway through another refresh's Rust+managed commit boundary.
     */
    fun beginRefresh(): RefreshTicket =
        synchronized(publicationLock) {
            refreshGeneration = Math.incrementExact(refreshGeneration)
            RefreshTicket(refreshGeneration)
        }

    fun commitAndPublish(
        ticket: RefreshTicket,
        keyboxes: List<CertHack.KeyBox>,
    ): PublicationResult =
        synchronized(publicationLock) {
            if (ticket.generation != refreshGeneration) return@synchronized PublicationResult.SUPERSEDED
            if (!KeyboxLoader.commitActive(keyboxes)) {
                Logger.e("Refusing to publish keyboxes because the Rust active-set commit failed")
                return@synchronized PublicationResult.FAILED
            }
            CertHack.setKeyboxes(keyboxes)
            committedIdentity = NativeBackend.currentBackendIdentity()
            PublicationResult.COMMITTED
        }

    /** Compatibility entry point for recovery/bootstrap callers that own their own error policy. */
    fun commitAndPublish(keyboxes: List<CertHack.KeyBox>): Boolean {
        val ticket = beginRefresh()
        return commitAndPublish(ticket, keyboxes) == PublicationResult.COMMITTED
    }

    fun invalidateBackendInstance() {
        synchronized(publicationLock) {
            refreshGeneration = Math.incrementExact(refreshGeneration)
            committedIdentity = null
            CertHack.clearCertificateCache()
        }
    }

    fun isCommittedForCurrentInstance(): Boolean {
        val committed = committedIdentity ?: return false
        return NativeBackend.isCurrentBackendIdentity(committed)
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        synchronized(publicationLock) {
            refreshGeneration = 0L
            committedIdentity = null
        }
    }
}