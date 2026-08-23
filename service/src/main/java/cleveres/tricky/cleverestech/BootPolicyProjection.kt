package cleveres.tricky.cleverestech

import org.json.JSONObject
import java.io.File

/**
 * Minimal early-boot policy projection.
 *
 * post-fs-data must not parse the full policy JSON: profiles can contain feature overrides and the
 * shell runs before the managed service is available. This projection contains only top-level
 * features that are allowed to affect device-wide pre-Zygote properties.
 */
internal object BootPolicyProjection {
    const val FILE_NAME = "boot_policy_state"
    private const val FORMAT_VERSION = 1
    private const val MAX_BYTES = 128L

    data class State(
        val buildIdentity: Boolean,
        val regionIdentity: Boolean,
        val identityRefresh: Boolean,
    )

    fun preflight(root: File) {
        SafeConfigStore.preflight(root, FILE_NAME)
    }

    fun desired(state: JSONObject): State {
        val features = state.optJSONObject("features") ?: JSONObject()
        return State(
            buildIdentity = features.optBoolean("buildIdentity", false),
            regionIdentity = features.optBoolean("regionIdentity", false),
            identityRefresh = features.optBoolean("identityRefresh", false),
        )
    }

    fun isSynchronized(
        root: File,
        state: JSONObject,
    ): Boolean = read(root) == desired(state)

    fun write(
        root: File,
        state: JSONObject,
    ) {
        val desired = desired(state)
        val text =
            buildString {
                append("version=").append(FORMAT_VERSION).append('\n')
                append("build=").append(if (desired.buildIdentity) 1 else 0).append('\n')
                append("region=").append(if (desired.regionIdentity) 1 else 0).append('\n')
                append("refresh=").append(if (desired.identityRefresh) 1 else 0).append('\n')
            }
        SafeConfigStore.writeText(root, FILE_NAME, text, MAX_BYTES)
    }

    fun read(root: File): State? {
        val text = SafeConfigStore.readText(root, FILE_NAME, MAX_BYTES, minBytes = 1) ?: return null
        val values = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            if (line.isEmpty()) return@forEach
            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.lastIndex) return null
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key !in setOf("version", "build", "region", "refresh") || values.put(key, value) != null) return null
        }
        if (values.size != 4 || values["version"] != FORMAT_VERSION.toString()) return null
        fun flag(name: String): Boolean? =
            when (values[name]) {
                "0" -> false
                "1" -> true
                else -> null
            }
        return State(
            buildIdentity = flag("build") ?: return null,
            regionIdentity = flag("region") ?: return null,
            identityRefresh = flag("refresh") ?: return null,
        )
    }
}
