package cleveres.tricky.cleverestech

import android.os.FileObserver
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class FilePoller(
    private val file: File,
    private val intervalMs: Long = 5000,
    private val onModified: (File) -> Unit,
) {
    private data class Snapshot(
        val exists: Boolean,
        val lastModified: Long,
        val length: Long,
    )

    @Volatile
    private var isRunning = false
    private var lastSnapshot = snapshot()
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var observer: FileObserver? = null

    companion object {
        private val scheduler =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "FilePoller-Fallback").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            }
    }

    init {
        require(intervalMs > 0) { "intervalMs must be positive" }
    }

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true
        lastSnapshot = snapshot()
        startObserver()
        scheduleFallbackPolling()
    }

    private fun startObserver(): Boolean {
        return try {
            val parent = file.parentFile
            if (parent == null || !parent.isDirectory) return false

            val eventMask =
                FileObserver.CLOSE_WRITE or
                    FileObserver.MOVED_TO or
                    FileObserver.MOVED_FROM or
                    FileObserver.CREATE or
                    FileObserver.DELETE or
                    FileObserver.ATTRIB

            @Suppress("DEPRECATION")
            val fileObserver =
                object : FileObserver(parent.absolutePath, eventMask) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        if (path != file.name) return
                        try {
                            checkForChange()
                        } catch (error: Throwable) {
                            Logger.e("FilePoller: Observer check failed for ${file.name}", error)
                        }
                    }
                }
            fileObserver.startWatching()
            observer = fileObserver
            true
        } catch (error: Throwable) {
            Logger.e("FilePoller: Could not start FileObserver for ${file.name}; periodic polling remains active", error)
            false
        }
    }

    private fun scheduleFallbackPolling() {
        scheduledFuture =
            scheduler.scheduleWithFixedDelay(
                {
                    try {
                        checkForChange()
                    } catch (error: Throwable) {
                        Logger.e("FilePoller: Periodic check failed for ${file.name}", error)
                    }
                },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS,
            )
    }

    @Synchronized
    private fun checkForChange() {
        if (!isRunning) return
        val current = snapshot()
        if (current == lastSnapshot) return
        lastSnapshot = current
        onModified(file)
    }

    @Synchronized
    fun stop() {
        isRunning = false
        observer?.stopWatching()
        observer = null
        scheduledFuture?.cancel(false)
        scheduledFuture = null
    }

    @Synchronized
    fun updateLastModified() {
        lastSnapshot = snapshot()
    }

    private fun snapshot(): Snapshot {
        val exists = Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
        return Snapshot(
            exists = exists,
            lastModified = if (exists) file.lastModified() else 0L,
            length = if (exists) file.length() else 0L,
        )
    }
}
