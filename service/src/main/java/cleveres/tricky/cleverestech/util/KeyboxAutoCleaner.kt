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
    private val webPortFile = File(configDir, "web_port")

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
            cleveres.tricky.cleverestech.CboxManager.refresh()
            cleveres.tricky.cleverestech.Config.updateKeyBoxes()
            notifyUser(revokedCount)
        }
        Logger.i("AutoCleaner: Finished check. Revoked/Invalid files moved: $revokedCount")
    }

    private fun notifyUser(count: Int) {
        try {
            val url = readWebUiUrl()
            Logger.d("AutoCleaner: Posting notification for WebUI at $url")
            // Post a high-priority, actionable notification
            val cmd = arrayOf(
                "su", "-c", "cmd notification post -S bigtext -t CleveresTricky 'Keybox Revoked Alert' '$count keybox(es) were found to be revoked/invalid and have been disabled. Check WebUI!' -a 'android.intent.action.VIEW' -d '$url'"
            )
            val process = Runtime.getRuntime().exec(cmd)
            val stdout = process.inputStream.bufferedReader().use { it.readText().trim() }
            val stderr = process.errorStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (stdout.isNotBlank()) {
                Logger.d("AutoCleaner: notification stdout: $stdout")
            }
            if (stderr.isNotBlank()) {
                Logger.d("AutoCleaner: notification stderr: $stderr")
            }
            if (exitCode != 0) {
                Logger.e("AutoCleaner: Failed to send notification (exit=$exitCode)")
            }
        } catch (e: Exception) {
            Logger.e("AutoCleaner: Failed to send notification", e)
        }
    }

    private fun readWebUiUrl(): String {
        return try {
            val raw = webPortFile.readText().trim()
            val parts = raw.split('|', limit = 2)
            val port = parts.getOrNull(0)?.toIntOrNull()
            val token = parts.getOrNull(1)?.trim().orEmpty()
            if (port == null || token.isBlank()) {
                Logger.e("AutoCleaner: Invalid web_port content '$raw'")
                "http://127.0.0.1:5623"
            } else {
                "http://127.0.0.1:$port/?token=$token"
            }
        } catch (e: Exception) {
            Logger.e("AutoCleaner: Failed to read WebUI endpoint metadata", e)
            "http://127.0.0.1:5623"
        }
    }
}
