package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Keeps V2 policy state compatible with early-boot code that still consumes
 * fixed marker files before the Android service is available.
 *
 * The file names are intentionally closed over a fixed allow-list. All paths are
 * checked without following links before any mutation so a malformed or replaced
 * marker fails closed instead of turning a policy update into an arbitrary file
 * write/delete primitive.
 */
internal object LegacyIdentityMarkers {
    const val ENGINE = "spoof_enabled"
    const val BUILD = "spoof_build_identity"
    const val TELEPHONY = "telephony"
    const val REGION = "spoof_region_cn"
    const val REFRESH = "random_on_boot"

    private const val ROOT_ONLY_MODE = 384 // 0600

    data class DesiredState(
        val engine: Boolean,
        val build: Boolean,
        val telephony: Boolean,
        val region: Boolean,
        val refresh: Boolean,
    )

    internal data class Operation(
        val name: String,
        val file: File,
        val enabled: Boolean,
    )

    private val allowedNames = setOf(ENGINE, BUILD, TELEPHONY, REGION, REFRESH)

    /** Validate all compatibility paths before a persistent policy mutation. */
    fun preflight(root: File) {
        allowedNames.forEach { name -> validatePath(File(root, name)) }
    }

    /**
     * Synchronize only explicit V2 policy. Legacy state already uses these marker
     * files as its source of truth and must never be rewritten from a derived view.
     */
    fun syncFromPolicyState(
        root: File,
        state: JSONObject,
    ): Result<Unit> =
        runCatching {
            if (!state.optString("source").equals("v2", ignoreCase = true)) return@runCatching
            apply(plan(root, desiredState(state)))
        }

    fun plan(
        root: File,
        desired: DesiredState,
    ): List<Operation> {
        val expected =
            linkedMapOf(
                ENGINE to desired.engine,
                BUILD to desired.build,
                TELEPHONY to desired.telephony,
                REGION to desired.region,
                REFRESH to desired.refresh,
            )
        val result = ArrayList<Operation>(expected.size)
        for ((name, enabled) in expected) {
            require(name in allowedNames) { "Unsupported identity marker" }
            val file = File(root, name)
            val exists = validatePath(file)
            if (exists != enabled) result += Operation(name, file, enabled)
        }
        return result
    }

    fun apply(operations: List<Operation>) {
        // Revalidate the complete plan immediately before mutating anything. This
        // prevents a pre-existing symlink/directory from causing a partial update.
        operations.forEach { operation ->
            require(operation.name in allowedNames) { "Unsupported identity marker" }
            validatePath(operation.file)
        }
        operations.forEach { operation ->
            if (operation.enabled) {
                SecureFile.touch(operation.file, ROOT_ONLY_MODE)
            } else {
                Files.deleteIfExists(operation.file.toPath())
            }
            // Update in-process legacy caches without waiting for the file observer.
            Config.refreshRuntimeSetting(operation.name)
        }
    }

    private fun desiredState(state: JSONObject): DesiredState {
        val resolved = LinkedHashMap<String, Boolean>()
        val features = state.getJSONObject("features")
        listOf("buildIdentity", "attestationIdentity", "telephonyIdentity", "regionIdentity", "identityRefresh")
            .forEach { key -> resolved[key] = features.getBoolean(key) }

        val activeProfile = state.optString("activeProfile").trim().takeIf { it.isNotEmpty() }
        if (activeProfile != null) {
            val profiles = state.optJSONArray("profiles")
            if (profiles != null) {
                for (index in 0 until profiles.length()) {
                    val profile = profiles.optJSONObject(index) ?: continue
                    if (!profile.optString("name").equals(activeProfile, ignoreCase = true)) continue
                    val overrides = profile.optJSONObject("features") ?: break
                    resolved.keys.toList().forEach { key ->
                        if (overrides.has(key) && overrides.opt(key) is Boolean) {
                            resolved[key] = overrides.getBoolean(key)
                        }
                    }
                    break
                }
            }
        }

        val build = resolved.getValue("buildIdentity")
        val attestation = resolved.getValue("attestationIdentity")
        val telephony = resolved.getValue("telephonyIdentity")
        val region = resolved.getValue("regionIdentity")
        val refresh = resolved.getValue("identityRefresh")
        // Security Patch intentionally does not participate in the legacy master
        // marker. V2 treats patch presentation as an independent identity feature.
        return DesiredState(
            engine = build || attestation || telephony || region || refresh,
            build = build,
            telephony = telephony,
            region = region,
            refresh = refresh,
        )
    }

    private fun validatePath(file: File): Boolean {
        val path = file.toPath()
        val exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS)
        if (exists && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Identity compatibility marker is not a regular file: ${file.name}")
        }
        return exists
    }
}
