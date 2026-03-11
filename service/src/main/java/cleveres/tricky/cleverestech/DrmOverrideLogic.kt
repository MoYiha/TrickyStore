package cleveres.tricky.cleverestech

internal object DrmOverrideLogic {
    const val SECURITY_LEVEL_PROPERTY = "securityLevel"
    const val DEVICE_UNIQUE_ID_PROPERTY = "deviceUniqueId"

    fun findTrackedPropertyName(observedStrings: List<String?>): String? {
        return observedStrings.firstOrNull {
            it == SECURITY_LEVEL_PROPERTY || it == DEVICE_UNIQUE_ID_PROPERTY
        }
    }

    fun spoofSecurityLevel(requestedProperty: String?, originalValue: String, configuredLevel: String?): String? {
        if (requestedProperty != SECURITY_LEVEL_PROPERTY) return null
        if (originalValue != "L2" && originalValue != "L3") return null

        val normalizedLevel = configuredLevel
            ?.trim()
            ?.removePrefix("L")
            ?.toIntOrNull()
            ?.takeIf { it in 1..3 }
            ?: 1
        return "L$normalizedLevel"
    }

    fun shouldSpoofDeviceUniqueId(requestedProperty: String?, randomizeOnBootEnabled: Boolean): Boolean {
        return randomizeOnBootEnabled && requestedProperty == DEVICE_UNIQUE_ID_PROPERTY
    }
}
