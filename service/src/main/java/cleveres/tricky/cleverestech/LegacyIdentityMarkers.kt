package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

internal object LegacyIdentityMarkers {
    const val ENGINE = "spoof_enabled"
    const val BUILD = "spoof_build_identity"
    const val TELEPHONY = "telephony"
    const val REGION = "spoof_region_cn"
    const val REFRESH = "random_on_boot"

    private const val ROOT_ONLY_MODE = 384
    private val allowedNames = setOf(ENGINE, BUILD, TELEPHONY, REGION, REFRESH)

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

    fun preflight(root: File) {
        allowedNames.forEach { validatePath(File(root, it)) }
    }

    fun isSynchronized(root: File, state: JSONObject): Result<Boolean> = runCatching {
        if (!state.optString("source").equals("v2", true)) return@runCatching true
        plan(root, desiredState(state)).isEmpty()
    }

    fun syncFromPolicyState(root: File, state: JSONObject): Result<Unit> = runCatching {
        if (!state.optString("source").equals("v2", true)) return@runCatching
        apply(plan(root, desiredState(state)))
    }

    fun plan(root: File, desired: DesiredState): List<Operation> {
        val expected = linkedMapOf(
            ENGINE to desired.engine,
            BUILD to desired.build,
            TELEPHONY to desired.telephony,
            REGION to desired.region,
            REFRESH to desired.refresh,
        )
        return expected.mapNotNull { (name, enabled) ->
            val file = File(root, name)
            if (validatePath(file) != enabled) Operation(name, file, enabled) else null
        }
    }

    /**
     * Applies the marker transaction with rollback. Marker files are compatibility
     * state; never leave a half-applied policy after an IO or runtime-refresh failure.
     */
    fun apply(
        operations: List<Operation>,
        refreshRuntime: (String) -> Unit = { Config.refreshRuntimeSetting(it) },
    ) {
        operations.forEach {
            require(it.name in allowedNames)
            validatePath(it.file)
        }

        val previous = operations.associate { it.file to Files.exists(it.file.toPath(), LinkOption.NOFOLLOW_LINKS) }
        val changed = ArrayList<Operation>()
        try {
            operations.forEach {
                // Register the operation before mutating the filesystem. SecureFile.touch()
                // can create the marker and then fail while enforcing permissions; that
                // partially-applied current operation must be rolled back as well.
                changed += it
                if (it.enabled) {
                    SecureFile.touch(it.file, ROOT_ONLY_MODE)
                } else {
                    Files.deleteIfExists(it.file.toPath())
                }
            }
            changed.forEach { refreshRuntime(it.name) }
        } catch (failure: Throwable) {
            changed.asReversed().forEach { op ->
                runCatching {
                    val shouldExist = previous[op.file] == true
                    if (shouldExist) SecureFile.touch(op.file, ROOT_ONLY_MODE)
                    else Files.deleteIfExists(op.file.toPath())
                }
            }
            // File rollback alone is insufficient if a runtime refresh failed after
            // earlier flags were already published. Re-read every touched marker from
            // the rolled-back filesystem so process state converges to the same snapshot.
            changed.forEach { op -> runCatching { refreshRuntime(op.name) } }
            throw failure
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun desiredState(state: JSONObject): DesiredState {
        val resolved = LinkedHashMap<String, Boolean>()
        val features = state.optJSONObject("features") ?: JSONObject()
        listOf("buildIdentity", "attestationIdentity", "telephonyIdentity", "regionIdentity", "identityRefresh")
            .forEach { key -> resolved[key] = features.optBoolean(key, false) }

        // JSON null is the canonical representation of "no active profile". Do not
        // coerce JSONObject.NULL through optString(): Android org.json renders that
        // sentinel as the literal string "null", which is also a valid profile name.
        val activeProfile =
            if (state.has("activeProfile") && !state.isNull("activeProfile")) {
                (state.opt("activeProfile") as? String)?.trim().orEmpty()
            } else {
                ""
            }
        val profiles = state.optJSONArray("profiles")
        if (activeProfile.isNotEmpty() && profiles != null) {
            for (i in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(i) ?: continue
                if (!profile.optString("name").equals(activeProfile, true)) continue
                val overrides = profile.optJSONObject("features") ?: break
                resolved.keys.forEach { key ->
                    if (overrides.has(key)) resolved[key] = overrides.optBoolean(key, resolved[key] ?: false)
                }
                break
            }
        }

        val build = resolved["buildIdentity"] ?: false
        val attestation = resolved["attestationIdentity"] ?: false
        val telephony = resolved["telephonyIdentity"] ?: false
        val region = resolved["regionIdentity"] ?: false
        val refresh = resolved["identityRefresh"] ?: false
        return DesiredState(build || attestation || telephony || region || refresh, build, telephony, region, refresh)
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
