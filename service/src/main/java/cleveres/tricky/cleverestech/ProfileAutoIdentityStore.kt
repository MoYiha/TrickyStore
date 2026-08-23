package cleveres.tricky.cleverestech

import org.json.JSONObject
import java.io.File

/** Persisted app-scoped Auto Identity snapshot, isolated from global spoof_build_vars. */
internal object ProfileAutoIdentityStore {
    const val FILE_NAME = "profile_auto_identity_vars"
    private const val FORMAT_VERSION = 2
    private const val MAX_BYTES = 64L * 1024L
    private const val MAX_ENTRIES = 32
    private val allowedKeys =
        listOf(
            "BRAND",
            "DEVICE",
            "PRODUCT",
            "MANUFACTURER",
            "MODEL",
            "FINGERPRINT",
            "RELEASE",
            "BUILD_ID",
            "INCREMENTAL",
            "TYPE",
            "TAGS",
            "SECURITY_PATCH",
        )
    private val allowedKeySet = allowedKeys.toSet()

    private data class Snapshot(
        val generation: Long,
        val updatedAtMs: Long,
        val values: Map<String, String>,
    )

    @Volatile
    private var snapshot = Snapshot(0, 0, emptyMap())

    fun get(key: String): String? = snapshot.values[key]

    fun generation(): Long = snapshot.generation

    fun updatedAtMs(): Long = snapshot.updatedAtMs

    fun diagnosticsJson(): JSONObject =
        JSONObject()
            .put("generation", snapshot.generation)
            .put("updatedAtMs", snapshot.updatedAtMs)
            .put("ready", snapshot.values.isNotEmpty())

    @Synchronized
    fun save(
        configDir: File,
        result: AutoIdentityManager.Result,
        nowMs: Long = System.currentTimeMillis(),
    ): Result<Unit> =
        runCatching {
            require(nowMs >= 0) { "Invalid Profile Auto Identity timestamp" }
            val updates = canonicalEntries(result)
            validateEntries(updates)
            val currentGeneration = snapshot.generation
            require(currentGeneration < Long.MAX_VALUE) { "Profile Auto Identity generation exhausted" }
            val nextGeneration = currentGeneration + 1
            val content =
                buildString {
                    append("version=").append(FORMAT_VERSION).append('\n')
                    append("generation=").append(nextGeneration).append('\n')
                    append("updated_at_ms=").append(nowMs).append('\n')
                    allowedKeys.forEach { key -> append(key).append('=').append(updates.getValue(key)).append('\n') }
                }
            SafeConfigStore.writeText(configDir, FILE_NAME, content, MAX_BYTES)
            snapshot = Snapshot(nextGeneration, nowMs, updates.toMap())
            Logger.i("Profile Auto Identity snapshot updated (generation=$nextGeneration)")
        }.onFailure { error ->
            Logger.e("Failed to persist Profile Auto Identity snapshot", error)
        }

    @Synchronized
    fun load(configDir: File): Result<Unit> =
        runCatching {
            val text = SafeConfigStore.readText(configDir, FILE_NAME, MAX_BYTES)
            if (text == null) {
                snapshot = Snapshot(0, 0, emptyMap())
                return@runCatching
            }
            val parsed = parse(text)
            snapshot = parsed
        }.onFailure { error ->
            snapshot = Snapshot(0, 0, emptyMap())
            Logger.e("Failed to load Profile Auto Identity snapshot; profile Auto Identity is unavailable", error)
        }

    private fun parse(text: String): Snapshot {
        val raw = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val separator = trimmed.indexOf('=')
            require(separator in 1 until trimmed.lastIndex) { "Invalid Profile Auto Identity entry" }
            val key = trimmed.substring(0, separator).trim()
            val value = trimmed.substring(separator + 1).trim()
            require(raw.put(key, value) == null) { "Duplicate Profile Auto Identity field" }
            require(raw.size <= MAX_ENTRIES) { "Too many Profile Auto Identity fields" }
        }

        val isVersioned = raw.containsKey("version")
        val generation: Long
        val updatedAtMs: Long
        if (isVersioned) {
            require(raw.remove("version") == FORMAT_VERSION.toString()) { "Unsupported Profile Auto Identity version" }
            generation = raw.remove("generation")?.toLongOrNull()?.takeIf { it > 0 } ?: error("Invalid Profile Auto Identity generation")
            updatedAtMs = raw.remove("updated_at_ms")?.toLongOrNull()?.takeIf { it >= 0 } ?: error("Invalid Profile Auto Identity timestamp")
        } else {
            // v1 shipped as only canonical Build key/value lines. Keep it readable for upgrades;
            // the next refresh writes v2 with an explicit generation.
            generation = 1
            updatedAtMs = 0
        }

        require(raw.keys == allowedKeySet) { "Profile Auto Identity snapshot is incomplete" }
        validateEntries(raw)
        val ordered = LinkedHashMap<String, String>()
        allowedKeys.forEach { key -> ordered[key] = raw.getValue(key) }
        return Snapshot(generation, updatedAtMs, ordered)
    }

    private fun canonicalEntries(result: AutoIdentityManager.Result): LinkedHashMap<String, String> {
        val entries = LinkedHashMap(result.buildVars())
        if (!entries.containsKey("RELEASE")) {
            val release = result.fingerprint.substringAfter(':', "").substringBefore('/').trim()
            require(release.isNotEmpty()) { "Auto Identity fingerprint does not contain an Android release" }
            entries["RELEASE"] = release
        }
        val ordered = LinkedHashMap<String, String>()
        allowedKeys.forEach { key -> entries[key]?.let { ordered[key] = it } }
        return ordered
    }

    private fun validateEntries(entries: Map<String, String>) {
        require(entries.keys == allowedKeySet) { "Profile Auto Identity snapshot is incomplete" }
        entries.forEach { (key, value) ->
            require(Config.isValidBuildVarEntry(key, value)) { "Invalid Profile Auto Identity field" }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        snapshot = Snapshot(0, 0, emptyMap())
    }
}
