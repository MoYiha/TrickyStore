package cleveres.tricky.cleverestech

import android.os.FileObserver
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class FilePoller(
    private val file: File,
    private val intervalMs: Long = 5000,
    private val onModified: (File) -> Unit
) {
    @Volatile
    private var isRunning = false
    @Volatile
    private var lastModified: Long = 0
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var observer: FileObserver? = null

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            val t = Thread(r, "FilePoller-Scheduler")
            t.isDaemon = true
            t
        }
    }

    init {
        if (file.exists()) {
            lastModified = file.lastModified()
        }
    }

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true

        try {
            val parent = file.parentFile
            if (parent != null && parent.exists()) {
                val observer = object : FileObserver(parent.absolutePath, CLOSE_WRITE or MOVED_TO) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == file.name && file.exists()) {
                            val currentModified = file.lastModified()
                            if (currentModified > lastModified) {
                                lastModified = currentModified
                                onModified(file)
                            }
                        }
                    }
                }
                observer.startWatching()
                this.observer = observer
                return
            }
        } catch (t: Throwable) {
            // Fallback to polling if FileObserver fails (e.g. on unit tests or unsupported environment)
        }

        scheduledFuture = scheduler.scheduleWithFixedDelay({
            if (!isRunning) return@scheduleWithFixedDelay // Double check
            try {
                if (file.exists()) {
                    val currentModified = file.lastModified()
                    if (currentModified > lastModified) {
                        lastModified = currentModified
                        onModified(file)
                    }
                }
            } catch (e: Throwable) {
                // Prevent thread death
                e.printStackTrace()
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun stop() {
        isRunning = false
        observer?.stopWatching()
        observer = null
        scheduledFuture?.cancel(false)
        scheduledFuture = null
    }

    fun updateLastModified() {
         if (file.exists()) {
            lastModified = file.lastModified()
        } else {
            lastModified = 0
        }
    }
}
