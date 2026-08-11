package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Config
import cleveres.tricky.cleverestech.Logger
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object KeyboxAutoCleaner {
    private val executorLock = Any()

    @Volatile
    private var executor: ScheduledExecutorService? = null
    private val configDir = File("/data/adb/cleverestricky")
    private val keyboxDir = File(configDir, "keyboxes")
    private val revokedDir = File(keyboxDir, "revoked")
    private val toggleFile = File(configDir, "auto_keybox_check")
    private val spoofEnabledFile = File(configDir, "spoof_enabled")

    fun start() {
        setEnabled(Config.isSpoofEnabled && isRegularFile(toggleFile))
    }

    fun setEnabled(enabled: Boolean) {
        synchronized(executorLock) {
            val current = executor
            if (!enabled) {
                current?.shutdownNow()
                executor = null
                return
            }
            if (current != null && !current.isShutdown) return

            val created =
                Executors.newSingleThreadScheduledExecutor { runnable ->
                    Thread(runnable, "CleveresTricky-KeyboxCheck").apply {
                        isDaemon = true
                        priority = Thread.MIN_PRIORITY
                    }
                }
            created.scheduleWithFixedDelay(
                {
                    try {
                        runCheck()
                    } catch (error: Throwable) {
                        Logger.e("AutoCleaner: Scheduled check failed", error)
                    }
                },
                1,
                1440,
                TimeUnit.MINUTES,
            )
            executor = created
        }
    }

    private fun isEnabledNow(): Boolean = Config.isSpoofEnabled && isRegularFile(spoofEnabledFile) && isRegularFile(toggleFile)

    private fun runCheck() {
        if (!isEnabledNow()) return

        Logger.i("AutoCleaner: Starting daily revocation check...")
        val results = KeyboxVerifier.verify(configDir)
        var revokedCount = 0
        var cancelled = false

        SecureFile.mkdirs(revokedDir, 448)

        for (res in results) {
            if (!isEnabledNow()) {
                cancelled = true
                break
            }
            if (res.status == KeyboxVerifier.Status.REVOKED || res.status == KeyboxVerifier.Status.INVALID) {
                Logger.i("AutoCleaner: Keybox ${res.filename} is ${res.status}. Moving to revoked.")
                val file = res.file
                if (file.exists() && File(res.filename).name == res.filename) {
                    try {
                        val initialTarget = File(revokedDir, res.filename)
                        val target =
                            if (initialTarget.exists()) {
                                File(revokedDir, "${res.filename}.${System.currentTimeMillis()}.revoked")
                            } else {
                                initialTarget
                            }
                        try {
                            Files.move(
                                file.toPath(),
                                target.toPath(),
                                StandardCopyOption.ATOMIC_MOVE,
                            )
                        } catch (_: AtomicMoveNotSupportedException) {
                            Files.move(file.toPath(), target.toPath())
                        }
                        revokedCount++
                    } catch (e: Exception) {
                        Logger.e("AutoCleaner: Failed to move ${res.filename}", e)
                    }
                }
            }
        }

        Config.updateKeyBoxesSync()
        if (!cancelled && revokedCount > 0) notifyUser(revokedCount)
        if (cancelled) {
            Logger.i("AutoCleaner: Check stopped because automatic cleanup was disabled")
        } else {
            Logger.i("AutoCleaner: Finished check. Revoked/Invalid files moved: $revokedCount")
        }
    }

    private fun notifyUser(count: Int) {
        try {
            val cmd =
                arrayOf(
                    "cmd",
                    "notification",
                    "post",
                    "-S",
                    "bigtext",
                    "-t",
                    "CleveresTricky",
                    "Keybox Revoked Alert",
                    "$count keybox(es) were revoked or invalid and have been disabled. Check WebUI.",
                )
            val nullDevice = File("/dev/null")
            val process =
                ProcessBuilder(*cmd)
                    .redirectOutput(nullDevice)
                    .redirectError(nullDevice)
                    .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Logger.e("AutoCleaner: Notification command timed out")
                return
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                Logger.e("AutoCleaner: Failed to send notification (exit=$exitCode)")
            }
        } catch (e: Exception) {
            Logger.e("AutoCleaner: Failed to send notification", e)
        }
    }

    private fun isRegularFile(file: File): Boolean = Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
}
