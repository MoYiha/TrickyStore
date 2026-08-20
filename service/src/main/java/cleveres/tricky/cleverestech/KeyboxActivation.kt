package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import java.util.concurrent.locks.ReentrantReadWriteLock

/** Atomic process-wide publication boundary between Rust secret keys and managed selection state. */
internal object KeyboxActivation {
    internal data class RefreshTicket internal constructor(val generation: Long)

    internal enum class PublicationResult {
        COMMITTED,
        SUPERSEDED,
        FAILED,
    }

    private val publicationLock = ReentrantReadWriteLock()
    private val publicationReadLock = publicationLock.readLock()
    private val publicationWriteLock = publicationLock.writeLock()
    private var refreshGeneration = 0L

    @Volatile
    private var committedIdentity: NativeBackend.BackendIdentity? = null

    /**
     * Starts a new logical refresh. Generation assignment shares the publication write lock so a
     * refresh cannot become newer halfway through another refresh's Rust+managed commit boundary.
     */
    fun beginRefresh(): RefreshTicket {
        publicationWriteLock.lock()
        return try {
            refreshGeneration = Math.incrementExact(refreshGeneration)
            RefreshTicket(refreshGeneration)
        } finally {
            publicationWriteLock.unlock()
        }
    }

    /**
     * Commits the Rust active set and publishes the matching managed snapshot as one reader-visible
     * transition. Fresh certificate rewrites hold the read side of this lock while selecting and
     * consuming an opaque backend key, so they can observe either the old pair or the new pair but
     * never the Rust-new/managed-old gap between these two operations.
     */
    fun commitAndPublish(
        ticket: RefreshTicket,
        keyboxes: List<CertHack.KeyBox>,
    ): PublicationResult {
        publicationWriteLock.lock()
        return try {
            if (ticket.generation != refreshGeneration) return PublicationResult.SUPERSEDED
            if (!KeyboxLoader.commitActive(keyboxes)) {
                Logger.e("Refusing to publish keyboxes because the Rust active-set commit failed")
                return PublicationResult.FAILED
            }
            CertHack.setKeyboxes(keyboxes)
            committedIdentity = NativeBackend.currentBackendIdentity()
            PublicationResult.COMMITTED
        } finally {
            publicationWriteLock.unlock()
        }
    }

    /** Compatibility entry point for recovery/bootstrap callers that own their own error policy. */
    fun commitAndPublish(keyboxes: List<CertHack.KeyBox>): Boolean {
        val ticket = beginRefresh()
        return commitAndPublish(ticket, keyboxes) == PublicationResult.COMMITTED
    }

    /** Java-facing guard for a certificate rewrite that consumes managed + Rust key state. */
    @JvmStatic
    fun lockPublishedSnapshot() {
        publicationReadLock.lock()
    }

    @JvmStatic
    fun unlockPublishedSnapshot() {
        publicationReadLock.unlock()
    }

    fun invalidateBackendInstance() {
        publicationWriteLock.lock()
        try {
            refreshGeneration = Math.incrementExact(refreshGeneration)
            committedIdentity = null
            CertHack.clearCertificateCache()
        } finally {
            publicationWriteLock.unlock()
        }
    }

    fun isCommittedForCurrentInstance(): Boolean {
        val committed = committedIdentity ?: return false
        return NativeBackend.isCurrentBackendIdentity(committed)
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        publicationWriteLock.lock()
        try {
            refreshGeneration = 0L
            committedIdentity = null
        } finally {
            publicationWriteLock.unlock()
        }
    }
}
