package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Logger
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object KeyboxAutoCleaner {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val configDir = File("/data/adb/cleverestricky")
    private val keyboxDir = File(configDir, "keyboxes")
    private val revokedDir = File(keyboxDir, "revoked")
    private val toggleFile = File(configDir, "auto_keybox_check")

    fun start() {
        executor.scheduleAtFixedRate({
            runCheck()
        }, 1, 1440, TimeUnit.MINUTES) // Run 1 min after start, then every 24 hours
    }

    private fun runCheck() {
        if (!toggleFile.exists()) return

        Logger.i("AutoCleaner: Starting daily revocation check...")
        val results = KeyboxVerifier.verify(configDir)
        var revokedCount = 0

        if (!revokedDir.exists()) revokedDir.mkdirs()

        for (res in results) {
            if (res.status == KeyboxVerifier.Status.REVOKED || res.status == KeyboxVerifier.Status.INVALID) {
                Logger.i("AutoCleaner: Keybox ${res.filename} is ${res.status}. Moving to revoked.")
                val file = res.file
                val target = File(revokedDir, res.filename)
                if (file.exists()) {
                    try {
                        file.renameTo(target)
                        revokedCount++
                    } catch (e: Exception) {
                        Logger.e("AutoCleaner: Failed to move ${res.filename}", e)
                    }
                }
            }
        }

        if (revokedCount > 0) {
            notifyUser(revokedCount)
        }
        Logger.i("AutoCleaner: Finished check. Revoked/Invalid files moved: $revokedCount")
    }

    private fun notifyUser(count: Int) {
        try {
            val cmd = arrayOf(
                "cmd", "notification", "post",
                "-S", "bigtext",
                "-t", "CleveresTricky",
                "Keybox Revocation Alert",
                "$count keybox(es) were found to be revoked/invalid and have been disabled."
            )
            Runtime.getRuntime().exec(cmd)
        } catch (e: Exception) {
            Logger.e("AutoCleaner: Failed to send notification", e)
        }
    }
}
