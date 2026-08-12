package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Performs narrowly-scoped, loss-minimizing repairs for persisted policy state
 * after upgrades. The policy parser still owns schema validation; this helper
 * only removes references that are known to become stale when keybox files or
 * retired compatibility controls change between releases.
 */
object PolicyMigration {
    private const val STATE_FILE = "policy_state_v2.json"
    private const val LAST_GOOD_FILE = "policy_state_v2.last_good.json"
    private const val SCHEMA_VERSION = 2
    private const val MAX_STATE_BYTES = 512L * 1024
    private val keyboxPattern = Regex("[A-Za-z0-9_.-]{5,128}")

    fun sanitize(configRoot: File): Boolean {
        var changed = false
        changed = sanitizeFile(configRoot, File(configRoot, STATE_FILE)) || changed
        changed = sanitizeFile(configRoot, File(configRoot, LAST_GOOD_FILE)) || changed
        return changed
    }

    private fun sanitizeFile(
        configRoot: File,
        stateFile: File,
    ): Boolean {
        val path = stateFile.toPath()
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || stateFile.length() !in 1..MAX_STATE_BYTES) {
            return false
        }

        val json =
            runCatching { JSONObject(stateFile.readText(Charsets.UTF_8)) }
                .getOrElse { return false }
        if (json.optInt("version", -1) != SCHEMA_VERSION) return false

        var changed = false
        val profiles = json.optJSONArray("profiles")
        val profileNames = HashSet<String>()
        if (profiles != null) {
            for (index in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(index) ?: continue
                profile.optString("name").trim().takeIf(String::isNotEmpty)?.let(profileNames::add)

                if (profile.has("rkpPassthrough")) {
                    profile.remove("rkpPassthrough")
                    changed = true
                }

                if (!profile.isNull("keybox")) {
                    val keybox = profile.optString("keybox").trim()
                    if (keybox.isNotEmpty() && !isAvailableKeybox(configRoot, keybox)) {
                        profile.put("keybox", JSONObject.NULL)
                        changed = true
                        Logger.w("Removed stale profile keybox reference during policy migration")
                    }
                }
            }
        }

        if (!json.isNull("activeProfile")) {
            val active = json.optString("activeProfile").trim()
            if (active.isNotEmpty() && active !in profileNames) {
                json.put("activeProfile", JSONObject.NULL)
                changed = true
                Logger.w("Removed stale active profile reference during policy migration")
            }
        }

        if (!changed) return false
        SecureFile.writeText(stateFile, json.toString())
        return true
    }

    private fun isAvailableKeybox(
        configRoot: File,
        filename: String,
    ): Boolean {
        if (!keyboxPattern.matches(filename) || filename.startsWith('.')) return false
        val lower = filename.lowercase()
        if (!lower.endsWith(".xml") && !lower.endsWith(".cbox")) return false
        val candidates =
            arrayOf(
                File(configRoot, filename),
                File(File(configRoot, "keyboxes"), filename),
            )
        return candidates.any { candidate ->
            val path = candidate.toPath()
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
        }
    }
}
