package cleveres.tricky.cleverestech

import android.os.FileObserver
import android.os.FileObserver.ATTRIB
import android.os.FileObserver.CLOSE_WRITE
import android.os.FileObserver.CREATE
import android.os.FileObserver.DELETE
import android.os.FileObserver.DELETE_SELF
import android.os.FileObserver.MODIFY
import android.os.FileObserver.MOVED_FROM
import android.os.FileObserver.MOVED_TO
import android.os.FileObserver.MOVE_SELF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val INTEGRITY_TARGETED_DEBOUNCE_MS = 100L
private const val INTEGRITY_FULL_SETTLE_MS = 1_000L
private const val INTEGRITY_WRITE_GRACE_MS = 5_000L
private const val MAX_PENDING_PATHS = 64
private const val INTEGRITY_MANIFEST_FILENAME = "integrity_manifest.json"

/**
 * Watches the module directory for filesystem events and triggers integrity verification.
 *
 * FileObserver events are treated as invalidation hints rather than proof of tampering. Android
 * module managers and atomic file replacement can emit transient DELETE/MOVE/MODIFY sequences even
 * when the final module bytes are unchanged. Only a cryptographic verification of the settled final
 * filesystem state is allowed to report an integrity violation. A missing CLOSE_WRITE gets a
 * bounded grace period, after which the current bytes must still pass cryptographic verification.
 */
internal object ModuleIntegrityWatcher {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var targetedScheduler: ConflatedRefreshScheduler? = null
    private var fullScheduler: ConflatedRefreshScheduler? = null

    private var childObserver: FileObserver? = null
    private val subObservers = mutableListOf<FileObserver>()
    private var parentObserver: FileObserver? = null
    internal var parentObserverStarter: (FileObserver) -> Unit = { it.startWatching() }
    internal var childObserverStarter: (FileObserver) -> Unit = { it.startWatching() }
    internal var observerStopper: (FileObserver) -> Unit = { it.stopWatching() }

    @Volatile
    private var isRunning = false
    private val lock = Any()
    private var watcherGeneration = 0L
    private var childGeneration = 0L
    private var mutationEpoch = 0L
    private var lastMutationNanos = 0L
    private var fullReverificationPending = false

    private val pendingDirtyPaths = LinkedHashSet<String>()
    private val pendingWritePaths = LinkedHashMap<String, Long>()
    private var writeOverflowLastSeenNanos: Long? = null

    @androidx.annotation.VisibleForTesting
    internal var nanoTime: () -> Long = System::nanoTime

    @androidx.annotation.VisibleForTesting
    internal var writeGraceMs: Long = INTEGRITY_WRITE_GRACE_MS

    @androidx.annotation.VisibleForTesting
    internal var fullVerificationDelayMs: Long = INTEGRITY_FULL_SETTLE_MS

    @androidx.annotation.VisibleForTesting
    internal var fullVerifier: () -> IntegrityResult = { ModuleIntegrityVerifier.verifyFull() }

    @androidx.annotation.VisibleForTesting
    internal var singleFileVerifier: (String, ParsedManifest) -> IntegrityResult = { path, manifest ->
        ModuleIntegrityVerifier.verifySingleFile(path, manifest)
    }

    val watcherRegistrationCount = AtomicInteger(0)
    val eventCoalescedCount = AtomicInteger(0)
    val targetedVerificationExecutions = AtomicInteger(0)
    val fullVerificationExecutions = AtomicInteger(0)

    /** Starts watching the module directory, its parent, and manifest subdirectories. */
    fun start(
        directory: File,
        loadedManifest: ParsedManifest,
        violationHandler: (List<String>) -> Unit,
    ) {
        synchronized(lock) {
            if (isRunning) return
            val generation = ++watcherGeneration
            isRunning = true
            mutationEpoch = 0L
            lastMutationNanos = nanoTime()
            fullReverificationPending = false
            pendingDirtyPaths.clear()
            clearPendingWritesLocked()

            targetedScheduler =
                ConflatedRefreshScheduler(scope, INTEGRITY_TARGETED_DEBOUNCE_MS) {
                    val work =
                        synchronized(lock) {
                            if (!ownsWatcherGenerationLocked(generation)) {
                                return@ConflatedRefreshScheduler
                            }
                            if (fullReverificationPending) {
                                pendingDirtyPaths.clear()
                                eventCoalescedCount.incrementAndGet()
                                return@ConflatedRefreshScheduler
                            }
                            val snapshot = ArrayList(pendingDirtyPaths)
                            pendingDirtyPaths.clear()
                            if (snapshot.isEmpty()) return@ConflatedRefreshScheduler
                            snapshot to mutationEpoch
                        }
                    val pathsToVerify = work.first
                    val verificationEpoch = work.second
                    targetedVerificationExecutions.incrementAndGet()

                    for (relPath in pathsToVerify) {
                        val result = singleFileVerifier(relPath, loadedManifest)
                        if (result is IntegrityResult.Fail) {
                            synchronized(lock) {
                                if (!ownsWatcherGenerationLocked(generation)) {
                                    return@ConflatedRefreshScheduler
                                }
                                if (
                                    mutationEpoch != verificationEpoch ||
                                    relPath in pendingWritePaths ||
                                    fullReverificationPending
                                ) {
                                    eventCoalescedCount.incrementAndGet()
                                    scheduleFullCheckLocked()
                                    return@ConflatedRefreshScheduler
                                }
                                violationHandler(result.violations)
                            }
                            return@ConflatedRefreshScheduler
                        }
                    }
                }

            fullScheduler =
                ConflatedRefreshScheduler(scope, 0L) {
                    // Delay inside the worker so incoming events cannot keep resetting its timer.
                    delay(fullVerificationDelayMs)
                    val verificationEpoch =
                        synchronized(lock) {
                            if (!ownsWatcherGenerationLocked(generation)) {
                                return@ConflatedRefreshScheduler
                            }
                            if (!fullReverificationPending && !hasPendingWritesLocked()) {
                                return@ConflatedRefreshScheduler
                            }
                            val now = nanoTime()
                            if (
                                hasUnexpiredPendingWritesLocked(now) ||
                                now - lastMutationNanos < TimeUnit.MILLISECONDS.toNanos(fullVerificationDelayMs)
                            ) {
                                eventCoalescedCount.incrementAndGet()
                                fullScheduler?.submit()
                                return@ConflatedRefreshScheduler
                            }
                            fullReverificationPending = true
                            pendingDirtyPaths.clear()
                            if (childObserver == null && directory.exists()) {
                                try {
                                    tryArmChildLocked(directory, loadedManifest, violationHandler, generation)
                                } catch (error: Throwable) {
                                    fullReverificationPending = false
                                    Logger.e("Failed to re-arm integrity watcher before settled verification", error)
                                    violationHandler(
                                        listOf("Failed to re-arm integrity watcher: ${error.message}"),
                                    )
                                    return@ConflatedRefreshScheduler
                                }
                            }
                            mutationEpoch
                        }

                    fullVerificationExecutions.incrementAndGet()
                    val result = fullVerifier()
                    synchronized(lock) {
                        if (!ownsWatcherGenerationLocked(generation)) return@ConflatedRefreshScheduler
                        if (mutationEpoch != verificationEpoch) {
                            eventCoalescedCount.incrementAndGet()
                            fullScheduler?.submit()
                            return@ConflatedRefreshScheduler
                        }
                        fullReverificationPending = false
                        clearPendingWritesLocked()
                        if (result is IntegrityResult.Fail) violationHandler(result.violations)
                    }
                }

            directory.parentFile?.let { parent ->
                try {
                    val pObserver =
                        object : FileObserver(parent, CREATE or MOVED_TO or DELETE or MOVED_FROM) {
                            override fun onEvent(event: Int, path: String?) {
                                handleParentEvent(
                                    directory,
                                    loadedManifest,
                                    violationHandler,
                                    generation,
                                    event,
                                    path,
                                )
                            }
                        }
                    parentObserver = pObserver
                    parentObserverStarter(pObserver)
                    watcherRegistrationCount.incrementAndGet()
                } catch (error: Throwable) {
                    Logger.e("Failed to arm integrity parent watcher", error)
                    stop()
                    throw error
                }
            }

            if (directory.exists()) {
                try {
                    tryArmChildLocked(directory, loadedManifest, violationHandler, generation)
                } catch (error: Throwable) {
                    Logger.e("Failed to arm integrity child watcher at start - failing closed", error)
                    stop()
                    violationHandler(listOf("Failed to arm integrity child watcher: ${error.message}"))
                    throw error
                }
            } else {
                Logger.e("Module directory missing at watcher start - integrity violation")
                violationHandler(listOf("Module directory does not exist at watcher start"))
            }
        }
    }

    /** Handles parent-directory changes by revalidating the final module root after settling. */
    private fun handleParentEvent(
        directory: File,
        loadedManifest: ParsedManifest,
        violationHandler: (List<String>) -> Unit,
        generation: Long,
        event: Int,
        path: String?,
    ) {
        synchronized(lock) {
            if (!ownsWatcherGenerationLocked(generation) || path != directory.name) return
            recordMutationLocked()
            pendingDirtyPaths.clear()
            clearPendingWritesLocked()

            if ((event and (DELETE or MOVED_FROM)) != 0) {
                Logger.w("Module directory moved or removed - waiting for settled integrity verification")
                disarmChildLocked()
                scheduleFullCheckLocked()
                return
            }
            if ((event and (CREATE or MOVED_TO)) != 0) {
                Logger.w("Module directory appeared - re-arming and verifying settled state")
                if (directory.exists()) {
                    try {
                        tryArmChildLocked(directory, loadedManifest, violationHandler, generation)
                    } catch (error: Throwable) {
                        Logger.e("Failed to arm child watcher upon recreate - failing closed", error)
                        disarmChildLocked()
                        violationHandler(
                            listOf("Failed to arm integrity child watcher upon recreate: ${error.message}"),
                        )
                        return
                    }
                }
                scheduleFullCheckLocked()
            }
        }
    }

    /** Arms child and manifest-subdirectory observers for the current watcher generation. */
    private fun tryArmChildLocked(
        directory: File,
        loadedManifest: ParsedManifest,
        violationHandler: (List<String>) -> Unit,
        generation: Long,
    ) {
        if (!ownsWatcherGenerationLocked(generation) || childObserver != null || !directory.exists()) return
        val childToken = ++childGeneration
        try {
            val cObserver =
                object : FileObserver(
                    directory,
                    CREATE or CLOSE_WRITE or DELETE or MOVED_FROM or MOVED_TO or
                        MODIFY or ATTRIB or DELETE_SELF or MOVE_SELF,
                ) {
                    override fun onEvent(event: Int, path: String?) {
                        handleChildEvent(directory, loadedManifest, generation, childToken, event, path)
                    }
                }
            childObserver = cObserver
            childObserverStarter(cObserver)
            watcherRegistrationCount.incrementAndGet()

            val subdirs =
                loadedManifest.files
                    .mapNotNull {
                        val idx = it.path.lastIndexOf('/')
                        if (idx > 0) it.path.substring(0, idx) else null
                    }.distinct()
            for (subdirRel in subdirs) {
                val subDir = File(directory, subdirRel)
                if (subDir.isDirectory) {
                    val sObserver =
                        object : FileObserver(
                            subDir,
                            CREATE or CLOSE_WRITE or DELETE or MOVED_FROM or MOVED_TO or
                                MODIFY or ATTRIB or DELETE_SELF or MOVE_SELF,
                        ) {
                            override fun onEvent(event: Int, path: String?) {
                                handleSubdirectoryEvent(
                                    directory,
                                    loadedManifest,
                                    generation,
                                    childToken,
                                    subdirRel,
                                    event,
                                    path,
                                )
                            }
                        }
                    subObservers.add(sObserver)
                    childObserverStarter(sObserver)
                    watcherRegistrationCount.incrementAndGet()
                }
            }
        } catch (error: Throwable) {
            Logger.e("Failed to arm integrity child watcher - disarming and failing closed", error)
            disarmChildLocked()
            throw error
        }
    }

    private fun handleChildEvent(
        directory: File,
        loadedManifest: ParsedManifest,
        generation: Long,
        childToken: Long,
        event: Int,
        path: String?,
    ) {
        synchronized(lock) {
            if (!ownsChildGenerationLocked(generation, childToken)) return
            if ((event and (DELETE_SELF or MOVE_SELF)) != 0) {
                recordMutationLocked()
                pendingDirtyPaths.clear()
                clearPendingWritesLocked()
                Logger.w("Module directory self event - waiting for settled integrity verification")
                disarmChildLocked()
                scheduleFullCheckLocked()
                return
            }
            val affectedPath = path ?: return
            handlePathEventLocked(directory, loadedManifest, generation, childToken, affectedPath, event)
        }
    }

    private fun handleSubdirectoryEvent(
        directory: File,
        loadedManifest: ParsedManifest,
        generation: Long,
        childToken: Long,
        subdirRel: String,
        event: Int,
        path: String?,
    ) {
        synchronized(lock) {
            if (!ownsChildGenerationLocked(generation, childToken)) return
            if ((event and (DELETE_SELF or MOVE_SELF)) != 0) {
                recordMutationLocked()
                pendingDirtyPaths.clear()
                clearPendingWritesLocked()
                Logger.w("Critical subdirectory $subdirRel changed - waiting for settled verification")
                disarmChildLocked()
                scheduleFullCheckLocked()
                return
            }
            val affectedPath = path?.let { "$subdirRel/$it" } ?: return
            handlePathEventLocked(directory, loadedManifest, generation, childToken, affectedPath, event)
        }
    }

    /** Routes path events so only stable file states are hashed. */
    private fun handlePathEventLocked(
        directory: File,
        loadedManifest: ParsedManifest,
        generation: Long,
        childToken: Long,
        affectedPath: String,
        event: Int,
    ) {
        if (!ownsChildGenerationLocked(generation, childToken)) return
        val isManifest = affectedPath == INTEGRITY_MANIFEST_FILENAME
        if (!isManifest && ModuleIntegrityVerifier.isIgnoredFile(affectedPath)) return
        val affectedFile = File(directory, affectedPath)

        if ((event and (DELETE or MOVED_FROM)) != 0) {
            recordMutationLocked()
            pendingDirtyPaths.clear()
            pendingWritePaths.remove(affectedPath)
            scheduleFullCheckLocked()
            return
        }

        if ((event and MOVED_TO) != 0) {
            val requireFullVerification = fullReverificationPending
            recordMutationLocked()
            pendingWritePaths.remove(affectedPath)
            if (isManifest || affectedFile.isDirectory) {
                scheduleFullCheckLocked()
            } else if (requireFullVerification) {
                fullReverificationPending = true
                fullScheduler?.submit()
            } else {
                scheduleTargetedCheckLocked(affectedPath, generation, childToken)
            }
            return
        }

        if ((event and CLOSE_WRITE) != 0) {
            val requireFullVerification = fullReverificationPending
            recordMutationLocked()
            pendingWritePaths.remove(affectedPath)
            if (isManifest) {
                scheduleFullCheckLocked()
            } else if (requireFullVerification) {
                fullReverificationPending = true
                fullScheduler?.submit()
            } else {
                scheduleTargetedCheckLocked(affectedPath, generation, childToken)
            }
            return
        }

        if ((event and (CREATE or MODIFY)) != 0) {
            recordMutationLocked()
            pendingDirtyPaths.remove(affectedPath)
            if (affectedFile.isDirectory) {
                scheduleFullCheckLocked()
            } else {
                trackPendingWriteLocked(affectedPath)
                if (isManifest) fullReverificationPending = true
                fullScheduler?.submit()
            }
            return
        }

        if ((event and ATTRIB) != 0) {
            val requireFullVerification = fullReverificationPending
            recordMutationLocked()
            if (affectedPath in pendingWritePaths) {
                if (requireFullVerification) fullScheduler?.submit()
                return
            }
            if (isManifest || affectedFile.isDirectory) {
                scheduleFullCheckLocked()
            } else if (requireFullVerification) {
                fullReverificationPending = true
                fullScheduler?.submit()
            } else {
                scheduleTargetedCheckLocked(affectedPath, generation, childToken)
            }
        }
    }

    /** Queues a stable path for coalesced single-file verification. */
    private fun scheduleTargetedCheckLocked(
        relPath: String,
        generation: Long,
        childToken: Long,
    ) {
        if (!ownsChildGenerationLocked(generation, childToken)) return
        if (pendingDirtyPaths.contains(relPath)) {
            eventCoalescedCount.incrementAndGet()
            return
        }
        if (pendingDirtyPaths.size >= MAX_PENDING_PATHS) {
            pendingDirtyPaths.clear()
            scheduleFullCheckLocked()
            return
        }
        pendingDirtyPaths.add(relPath)
        targetedScheduler?.submit()
    }

    private fun recordMutationLocked() {
        mutationEpoch++
        lastMutationNanos = nanoTime()
    }

    /** Retains at most 64 paths; overflow keeps a conservative deadline for untracked writes. */
    private fun trackPendingWriteLocked(path: String) {
        if (path in pendingWritePaths) return
        val now = nanoTime()
        if (pendingWritePaths.size < MAX_PENDING_PATHS) {
            pendingWritePaths[path] = now
        } else {
            writeOverflowLastSeenNanos = now
            fullReverificationPending = true
        }
    }

    private fun hasPendingWritesLocked(): Boolean =
        pendingWritePaths.isNotEmpty() || writeOverflowLastSeenNanos != null

    /** Every unresolved write gets its own grace window before a settled full scan can run. */
    private fun hasUnexpiredPendingWritesLocked(now: Long): Boolean {
        val graceNanos = TimeUnit.MILLISECONDS.toNanos(writeGraceMs)
        return pendingWritePaths.values.any { now - it < graceNanos } ||
            writeOverflowLastSeenNanos?.let { now - it < graceNanos } == true
    }

    private fun clearPendingWritesLocked() {
        pendingWritePaths.clear()
        writeOverflowLastSeenNanos = null
    }

    /** Requires a settled full verification before any violation decision. */
    private fun scheduleFullCheckLocked() {
        fullReverificationPending = true
        fullScheduler?.submit()
    }

    private fun ownsWatcherGenerationLocked(generation: Long): Boolean =
        isRunning && watcherGeneration == generation

    private fun ownsChildGenerationLocked(
        generation: Long,
        childToken: Long,
    ): Boolean = ownsWatcherGenerationLocked(generation) && childGeneration == childToken

    private fun disarmChildLocked() {
        childGeneration++
        val retiredChild = childObserver
        childObserver = null
        val retiredSubs = subObservers.toList()
        subObservers.clear()
        runCatching { retiredChild?.let(observerStopper) }
            .onFailure { Logger.w("Failed to stop retired integrity child watcher", it) }
        for (observer in retiredSubs) {
            runCatching { observerStopper(observer) }
                .onFailure { Logger.w("Failed to stop retired integrity subdirectory watcher", it) }
        }
    }

    /** Stops all observers and cancels pending verification work. */
    fun stop() {
        synchronized(lock) {
            isRunning = false
            watcherGeneration++
            val retiredParent = parentObserver
            parentObserver = null
            disarmChildLocked()
            runCatching { retiredParent?.let(observerStopper) }
                .onFailure { Logger.w("Failed to stop retired integrity parent watcher", it) }
            targetedScheduler?.cancel()
            targetedScheduler = null
            fullScheduler?.cancel()
            fullScheduler = null
            fullReverificationPending = false
            pendingDirtyPaths.clear()
            clearPendingWritesLocked()
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun isChildObserverActiveForTesting(): Boolean =
        synchronized(lock) { childObserver != null }

    @androidx.annotation.VisibleForTesting
    internal fun isParentObserverActiveForTesting(): Boolean =
        synchronized(lock) { parentObserver != null }

    @androidx.annotation.VisibleForTesting
    internal fun injectChildEventForTesting(event: Int, path: String?) {
        synchronized(lock) { childObserver?.onEvent(event, path) }
    }

    @androidx.annotation.VisibleForTesting
    internal fun subObserverCountForTesting(): Int =
        synchronized(lock) { subObservers.size }

    @androidx.annotation.VisibleForTesting
    internal fun injectSubEventForTesting(index: Int, event: Int, path: String?) {
        synchronized(lock) {
            if (index in subObservers.indices) subObservers[index].onEvent(event, path)
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun injectParentEventForTesting(event: Int, path: String?) {
        synchronized(lock) { parentObserver?.onEvent(event, path) }
    }

    @androidx.annotation.VisibleForTesting
    internal fun pendingDirtyCountForTesting(): Int =
        synchronized(lock) { pendingDirtyPaths.size }

    @androidx.annotation.VisibleForTesting
    internal fun pendingWriteCountForTesting(): Int =
        synchronized(lock) { pendingWritePaths.size }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        stop()
        parentObserverStarter = { it.startWatching() }
        childObserverStarter = { it.startWatching() }
        observerStopper = { it.stopWatching() }
        nanoTime = System::nanoTime
        writeGraceMs = INTEGRITY_WRITE_GRACE_MS
        fullVerificationDelayMs = INTEGRITY_FULL_SETTLE_MS
        fullVerifier = { ModuleIntegrityVerifier.verifyFull() }
        singleFileVerifier = { path, manifest -> ModuleIntegrityVerifier.verifySingleFile(path, manifest) }
        mutationEpoch = 0L
        watcherRegistrationCount.set(0)
        eventCoalescedCount.set(0)
        targetedVerificationExecutions.set(0)
        fullVerificationExecutions.set(0)
    }
}
