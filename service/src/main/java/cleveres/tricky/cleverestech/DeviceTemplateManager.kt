package cleveres.tricky.cleverestech

import java.io.File
import kotlin.jvm.Synchronized
import org.json.JSONArray
import org.json.JSONObject
import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.util.SecureFile

data class DeviceTemplate(
    val id: String, // unique ID, e.g. "pixel8pro"
    val manufacturer: String,
    val model: String,
    val fingerprint: String,
    val brand: String,
    val product: String,
    val device: String,
    val release: String,
    val buildId: String,
    val incremental: String,
    val type: String = "user",
    val tags: String = "release-keys",
    val securityPatch: String
) {
    fun toPropMap(): Map<String, String> {
        return mapOf(
            "MANUFACTURER" to manufacturer,
            "MODEL" to model,
            "FINGERPRINT" to fingerprint,
            "BRAND" to brand,
            "PRODUCT" to product,
            "DEVICE" to device,
            "RELEASE" to release,
            "ID" to buildId,
            "INCREMENTAL" to incremental,
            "TYPE" to type,
            "TAGS" to tags,
            "SECURITY_PATCH" to securityPatch
        )
    }
}

object DeviceTemplateManager {
    private const val TEMPLATES_FILE = "templates.json"
    private var templates: MutableMap<String, DeviceTemplate> = mutableMapOf()
    private var cachedList: List<DeviceTemplate>? = null

    // Beta Starter Pack: Verified High-Value Fingerprints
    private val builtInTemplates = listOf(
        DeviceTemplate(
            id = "pixel8pro",
            manufacturer = "Google", model = "Pixel 8 Pro",
            fingerprint = "google/husky/husky:14/AP1A.240405.002/11480754:user/release-keys",
            brand = "google", product = "husky", device = "husky",
            release = "14", buildId = "AP1A.240405.002", incremental = "11480754", securityPatch = "2024-04-05"
        ),
        DeviceTemplate(
            id = "pixel8",
            manufacturer = "Google", model = "Pixel 8",
            fingerprint = "google/shiba/shiba:14/AP1A.240405.002/11480754:user/release-keys",
            brand = "google", product = "shiba", device = "shiba",
            release = "14", buildId = "AP1A.240405.002", incremental = "11480754", securityPatch = "2024-04-05"
        ),
        DeviceTemplate(
            id = "pixel7pro",
            manufacturer = "Google", model = "Pixel 7 Pro",
            fingerprint = "google/cheetah/cheetah:14/AP1A.240305.019.A1/11445699:user/release-keys",
            brand = "google", product = "cheetah", device = "cheetah",
            release = "14", buildId = "AP1A.240305.019.A1", incremental = "11445699", securityPatch = "2024-03-05"
        ),
        DeviceTemplate(
            id = "pixel6pro",
            manufacturer = "Google", model = "Pixel 6 Pro",
            fingerprint = "google/raven/raven:13/TQ3A.230901.001/10750268:user/release-keys",
            brand = "google", product = "raven", device = "raven",
            release = "13", buildId = "TQ3A.230901.001", incremental = "10750268", securityPatch = "2023-09-01"
        ),
        DeviceTemplate(
            id = "s24ultra",
            manufacturer = "samsung", model = "SM-S928B",
            fingerprint = "samsung/e3sxXX/e3s:14/UP1A.231005.007/S928BXXS1AXBG:user/release-keys",
            brand = "samsung", product = "e3sxXX", device = "e3s",
            release = "14", buildId = "UP1A.231005.007", incremental = "S928BXXS1AXBG", securityPatch = "2024-02-01"
        ),
        DeviceTemplate(
            id = "s23ultra",
            manufacturer = "samsung", model = "SM-S918B",
            fingerprint = "samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S918BXXS3BXE0:user/release-keys",
            brand = "samsung", product = "dm3qxxx", device = "dm3q",
            release = "14", buildId = "UP1A.231005.007", incremental = "S918BXXS3BXE0", securityPatch = "2024-05-01"
        ),
        DeviceTemplate(
            id = "xiaomi14",
            manufacturer = "Xiaomi", model = "23127PN0CG",
            fingerprint = "Xiaomi/houji_global/houji:14/UKQ1.230804.001/V816.0.4.0.UNCMIXM:user/release-keys",
            brand = "Xiaomi", product = "houji_global", device = "houji",
            release = "14", buildId = "UKQ1.230804.001", incremental = "V816.0.4.0.UNCMIXM", securityPatch = "2024-03-01"
        ),
        DeviceTemplate(
            id = "oneplus11",
            manufacturer = "OnePlus", model = "CPH2449",
            fingerprint = "OnePlus/CPH2449/OP5554L1:14/UKQ1.230924.001/R.15f1de6-1-1:user/release-keys",
            brand = "OnePlus", product = "CPH2449", device = "OP5554L1",
            release = "14", buildId = "UKQ1.230924.001", incremental = "R.15f1de6-1-1", securityPatch = "2024-04-05"
        ),
        DeviceTemplate(
            id = "nothing2",
            manufacturer = "Nothing", model = "A065",
            fingerprint = "Nothing/Pong/Pong:13/TKQ1.220915.002/2.5.1-231228-0054:user/release-keys",
            brand = "Nothing", product = "Pong", device = "Pong",
            release = "13", buildId = "TKQ1.220915.002", incremental = "2.5.1-231228-0054", securityPatch = "2024-01-01"
        )
    )

    @Synchronized
    fun initialize(configDir: File) {
        // 1. Load built-ins
        builtInTemplates.forEach { templates[it.id] = it }

        // 2. Load custom from JSON
        val file = File(configDir, TEMPLATES_FILE)
        if (file.exists()) {
            try {
                val json = file.readText()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val t = parseJson(obj)
                    if (t != null) templates[t.id] = t
                }
                Logger.i("Loaded ${array.length()} templates from $TEMPLATES_FILE")
            } catch (e: Exception) {
                Logger.e("Failed to load templates.json", e)
            }
        } else {
            // Save built-ins to file for user editing
            saveTemplates(configDir)
        }
        cachedList = null
    }

    private fun parseJson(obj: JSONObject): DeviceTemplate? {
        return try {
            DeviceTemplate(
                id = obj.getString("id"),
                manufacturer = obj.getString("manufacturer"),
                model = obj.getString("model"),
                fingerprint = obj.getString("fingerprint"),
                brand = obj.getString("brand"),
                product = obj.getString("product"),
                device = obj.getString("device"),
                release = obj.getString("release"),
                buildId = obj.getString("buildId"),
                incremental = obj.getString("incremental"),
                type = obj.optString("type", "user"),
                tags = obj.optString("tags", "release-keys"),
                securityPatch = obj.getString("securityPatch")
            )
        } catch (e: Exception) {
            Logger.e("Error parsing template JSON", e)
            null
        }
    }

    fun getTemplate(id: String): DeviceTemplate? {
        return templates[id]
    }

    fun getTemplateAsMap(id: String): Map<String, String>? {
        return templates[id]?.toPropMap()
    }

    @Synchronized
    fun listTemplates(): List<DeviceTemplate> {
        val current = cachedList
        if (current != null) return current

        val sorted = templates.values.toList().sortedBy { it.model }
        cachedList = sorted
        return sorted
    }

    fun saveTemplates(configDir: File) {
        try {
            val array = JSONArray()
            templates.values.forEach { t ->
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("manufacturer", t.manufacturer)
                obj.put("model", t.model)
                obj.put("fingerprint", t.fingerprint)
                obj.put("brand", t.brand)
                obj.put("product", t.product)
                obj.put("device", t.device)
                obj.put("release", t.release)
                obj.put("buildId", t.buildId)
                obj.put("incremental", t.incremental)
                obj.put("type", t.type)
                obj.put("tags", t.tags)
                obj.put("securityPatch", t.securityPatch)
                array.put(obj)
            }
            SecureFile.writeText(File(configDir, TEMPLATES_FILE), array.toString(4))
        } catch (e: Exception) {
            Logger.e("Failed to save templates.json", e)
        }
    }

    @Synchronized
    fun addTemplate(template: DeviceTemplate) {
        templates[template.id] = template
        cachedList = null
    }
}
