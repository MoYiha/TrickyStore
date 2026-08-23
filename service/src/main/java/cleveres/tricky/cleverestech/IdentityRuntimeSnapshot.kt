package cleveres.tricky.cleverestech

import org.json.JSONObject
import java.io.File

/** Captures genuine app-visible properties before a runtime Identity enable transition. */
internal object IdentityRuntimeSnapshot {
    const val FILE_NAME = "identity_runtime_original"
    private const val FORMAT_VERSION = 1
    private const val MAX_BYTES = 16L * 1024L

    val buildProperties =
        listOf(
            "ro.build.fingerprint",
            "ro.product.brand",
            "ro.product.device",
            "ro.product.name",
            "ro.product.manufacturer",
            "ro.product.model",
            "ro.build.id",
            "ro.build.version.release",
            "ro.build.version.release_or_codename",
            "ro.build.version.incremental",
            "ro.build.type",
            "ro.build.tags",
            "ro.build.version.security_patch",
        )
    val regionProperties =
        listOf(
            "ro.boot.hwc",
            "gsm.operator.iso-country",
            "gsm.sim.operator.iso-country",
            "ro.boot.hwlevel",
            "persist.radio.skhwc_matchres",
        )
    private val allowedProperties = (buildProperties + regionProperties).toSet()

    data class Snapshot(
        val buildCaptured: Boolean,
        val regionCaptured: Boolean,
        val values: Map<String, String>,
    )

    @Synchronized
    fun capture(
        root: File,
        build: Boolean,
        region: Boolean,
    ): Result<Snapshot> =
        runCatching {
            require(build || region) { "No runtime Identity group requested" }
            val existing = read(root)
            if (existing != null && (!build || existing.buildCaptured) && (!region || existing.regionCaptured)) {
                return@runCatching existing
            }

            val values = LinkedHashMap<String, String>()
            existing?.values?.let(values::putAll)
            var buildCaptured = existing?.buildCaptured == true
            var regionCaptured = existing?.regionCaptured == true
            if (build && !buildCaptured) {
                captureGroup(buildProperties, values)
                buildCaptured = true
            }
            if (region && !regionCaptured) {
                captureGroup(regionProperties, values)
                regionCaptured = true
            }

            val json = JSONObject()
                .put("version", FORMAT_VERSION)
                .put("build", buildCaptured)
                .put("region", regionCaptured)
                .put("values", JSONObject(values as Map<*, *>))
            SafeConfigStore.writeText(root, FILE_NAME, json.toString(), MAX_BYTES)
            Snapshot(buildCaptured, regionCaptured, values.toMap())
        }.onFailure { error ->
            Logger.w("Live Identity rollback snapshot is unavailable: ${error.javaClass.simpleName}")
        }

    @Synchronized
    fun read(root: File): Snapshot? {
        val text = SafeConfigStore.readText(root, FILE_NAME, MAX_BYTES, minBytes = 1) ?: return null
        val json = JSONObject(text)
        require(json.length() == 4 && json.getInt("version") == FORMAT_VERSION) { "Invalid runtime Identity snapshot" }
        val build = json.getBoolean("build")
        val region = json.getBoolean("region")
        val objectValues = json.getJSONObject("values")
        val values = LinkedHashMap<String, String>()
        val keys = objectValues.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(key in allowedProperties) { "Unexpected runtime Identity property" }
            val value = objectValues.getString(key)
            require(value.isNotEmpty() && value.length <= 512 && value.none { it.code < 0x20 || it.code == 0x7f }) {
                "Invalid runtime Identity property value"
            }
            values[key] = value
        }
        if (build) require(values.keys.containsAll(buildProperties)) { "Incomplete Build rollback snapshot" }
        if (region) require(values.keys.containsAll(regionProperties)) { "Incomplete region rollback snapshot" }
        return Snapshot(build, region, values)
    }

    @Synchronized
    fun clear(root: File) {
        SafeConfigStore.delete(root, FILE_NAME)
    }

    private fun captureGroup(
        properties: List<String>,
        destination: MutableMap<String, String>,
    ) {
        val captured = LinkedHashMap<String, String>()
        properties.forEach { property ->
            val value = systemPropertiesGet(property, "").orEmpty()
            require(value.isNotEmpty() && value.length <= 512 && value.none { it.code < 0x20 || it.code == 0x7f }) {
                "Property $property cannot be safely restored without reboot"
            }
            captured[property] = value
        }
        destination.putAll(captured)
    }
}
