package cleveres.tricky.cleverestech

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

object BootLogic {
    private const val CONFIG_PATH = "/data/adb/cleverestricky"
    private val ran = AtomicBoolean(false)
    private val configDir = File(CONFIG_PATH)

    // Flag Files
    const val FILE_AUTO_PATCH = "auto_patch_update"
    const val FILE_HIDE_PROPS = "hide_sensitive_props"
    const val FILE_SPOOF_CN = "spoof_region_cn"
    const val FILE_REMOVE_MAGISK32 = "remove_magisk_32"

    fun run() {
        if (ran.getAndSet(true)) return

        Logger.i("Running BootLogic tasks...")

        try {
            if (File(configDir, FILE_AUTO_PATCH).exists()) {
                checkAutoPatch()
            }

            // Check hiding props logic
            // User request: "Most default settings should be disabled"
            // So we only run if the file exists.
            if (File(configDir, FILE_HIDE_PROPS).exists()) {
                checkHideProps()
            }

            if (File(configDir, FILE_REMOVE_MAGISK32).exists()) {
                checkRemoveMagisk32()
            }

        } catch (e: Exception) {
            Logger.e("BootLogic failed", e)
        }
    }

    private fun checkAutoPatch() {
        try {
            val currentPatch = getSystemProperty("ro.build.version.security_patch")
            if (currentPatch.isBlank()) return

            // Parse current patch
            // It could be YYYY-MM-DD or something else
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val patchDate = try {
                LocalDate.parse(currentPatch, formatter)
            } catch (e: Exception) {
                Logger.e("Failed to parse security patch: $currentPatch", e)
                return
            }

            val sixMonthsAgo = LocalDate.now().minus(6, ChronoUnit.MONTHS)

            if (patchDate.isBefore(sixMonthsAgo)) {
                Logger.i("System security patch ($currentPatch) is older than 6 months. Updating...")

                // Calculate target date: 5th of previous month
                val now = LocalDate.now()
                val targetDate = now.minusMonths(1).withDayOfMonth(5)
                val newPatch = targetDate.format(formatter)

                val spFile = File(configDir, "security_patch.txt")
                if (!spFile.exists() || spFile.readText().trim() != newPatch) {
                    spFile.writeText(newPatch)
                    Logger.i("Updated security_patch.txt to $newPatch")
                    // Fix permissions
                    spFile.setReadable(true, true) // 0600 effectively if owner only?
                    // Runtime.exec("chmod 600 ...") might be safer to ensure correct perms
                    exec("chmod 600 ${spFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Logger.e("Error in Auto Patch Update", e)
        }
    }

    private fun checkHideProps() {
        try {
            val shamikoExists = File("/data/adb/modules/zygisk_shamiko").exists()
            val spoofCn = File(configDir, FILE_SPOOF_CN).exists()

            if (shamikoExists) {
                Logger.i("Shamiko detected. Applying minimal hiding.")
                if (spoofCn) {
                    resetProp("ro.boot.hwc", "CN")
                    resetProp("gsm.operator.iso-country", "cn")
                }
            } else {
                Logger.i("Shamiko not found. Applying comprehensive hiding.")

                // Helper to verify before setting to avoid log spam/overhead?
                // resetprop is fast enough usually.

                // Standard hiding props
                resetProp("ro.boot.vbmeta.device_state", "locked")
                resetProp("ro.boot.verifiedbootstate", "green")
                resetProp("ro.boot.flash.locked", "1")
                resetProp("ro.boot.veritymode", "enforcing")
                resetProp("ro.boot.warranty_bit", "0")
                resetProp("ro.warranty_bit", "0")
                resetProp("ro.debuggable", "0")
                resetProp("ro.force.debuggable", "0")
                resetProp("ro.secure", "1")
                resetProp("ro.adb.secure", "1")
                resetProp("ro.build.type", "user")
                resetProp("ro.build.tags", "release-keys")
                resetProp("ro.vendor.boot.warranty_bit", "0")
                resetProp("ro.vendor.warranty_bit", "0")
                resetProp("vendor.boot.vbmeta.device_state", "locked")
                resetProp("vendor.boot.verifiedbootstate", "green")
                resetProp("sys.oem_unlock_allowed", "0")
                resetProp("ro.secureboot.lockstate", "locked")

                // Realme specific
                resetProp("ro.boot.realmebootstate", "green")
                resetProp("ro.boot.realme.lockstate", "1")

                // Bootmode
                hideBootMode("ro.bootmode")
                hideBootMode("ro.boot.bootmode")
                hideBootMode("vendor.boot.bootmode")

                if (spoofCn) {
                    resetProp("ro.boot.hwc", "CN")
                    resetProp("gsm.operator.iso-country", "cn")
                    resetProp("gsm.sim.operator.iso-country", "cn")
                    resetProp("ro.boot.hwlevel", "MP")
                    resetProp("persist.radio.skhwc_matchres", "MATCH")
                }
            }

            // Always reset sys.boot_completed to 0 ?
            // The user script did `resetprop -w sys.boot_completed 0`
            // This might trigger listeners?
            // It seems the user script wanted to trigger something or hide boot completion status?
            // "resetprop -w sys.boot_completed 0"
            // I'll skip this as it might be dangerous/loop-inducing if not careful.

        } catch (e: Exception) {
            Logger.e("Error in Hide Props", e)
        }
    }

    private fun checkRemoveMagisk32() {
        try {
            val paths = listOf(
                "/debug_ramdisk/magisk32",
                "/data/adb/magisk/magisk32"
            )
            var count = 0
            paths.forEach { path ->
                val f = File(path)
                if (f.exists()) {
                    if (f.delete()) {
                        Logger.i("Deleted Magisk 32-bit binary: $path")
                        count++
                    } else {
                        // Try with shell if File.delete fails (e.g. read-only mount)
                        exec("rm -f $path")
                        if (!f.exists()) {
                             Logger.i("Deleted Magisk 32-bit binary via shell: $path")
                             count++
                        }
                    }
                }
            }
            if (count > 0) {
                Logger.i("Magisk 32-bit cleanup complete.")
            }
        } catch (e: Exception) {
            Logger.e("Error removing Magisk 32-bit", e)
        }
    }

    private fun resetProp(name: String, value: String) {
        try {
             // Check if already set to avoid spamming?
             // But resetprop forces it.
             exec("resetprop -n $name $value")
        } catch (e: Exception) {
            Logger.e("Failed to resetprop $name", e)
        }
    }

    private fun hideBootMode(name: String) {
        val current = getSystemProperty(name)
        if (current.contains("recovery") || current.contains("unknown")) {
             // User script: contains_reset_prop "ro.bootmode" "recovery" "unknown"
             // Interpretation: if prop contains "recovery", set it to "unknown"?
             // Script: [[ "$(resetprop $NAME)" = *"$CONTAINS"* ]] && resetprop $NAME $NEWVAL
             // So if it contains "recovery", set to "unknown".
             resetProp(name, "unknown")
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
            p.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            ""
        }
    }

    private fun exec(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
        } catch (e: Exception) {
            Logger.e("Exec failed: $cmd", e)
        }
    }
}
