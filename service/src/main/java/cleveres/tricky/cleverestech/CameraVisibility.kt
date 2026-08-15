package cleveres.tricky.cleverestech

internal const val MAX_VISIBLE_CAMERA_COUNT = 16
internal const val DEFAULT_CAMERA_DEVICE_ID = 0
internal const val CAMERA_STATUS_NOT_PRESENT = 0
internal const val CAMERA_STATUS_ENUMERATING = 2

internal fun shouldRunCameraVisibility(
    enabled: Boolean,
    configuredLimit: Int?,
): Boolean = enabled && configuredLimit != null

internal fun boundedVisibleCameraCount(
    realCount: Int,
    configuredLimit: Int?,
): Int {
    val real = realCount.coerceAtLeast(0)
    val limit = configuredLimit ?: return real
    return minOf(real, limit.coerceIn(0, MAX_VISIBLE_CAMERA_COUNT))
}

internal data class CameraVisibilityKey(
    val cameraId: String,
    val deviceId: Int = DEFAULT_CAMERA_DEVICE_ID,
)

internal data class CameraVisibilityStatus(
    val key: CameraVisibilityKey,
    val status: Int,
)

internal data class CameraVisibilityDelta(
    val hidden: Set<CameraVisibilityKey>,
    val shown: List<CameraVisibilityStatus>,
    val visible: Set<CameraVisibilityKey>,
)

internal fun isDiscoverableCameraStatus(status: Int): Boolean =
    status != CAMERA_STATUS_NOT_PRESENT && status != CAMERA_STATUS_ENUMERATING

/**
 * Tracks the complete camera snapshot while exposing a bounded subset per Android device context.
 *
 * Modern Android stores camera status for multiple virtual-device contexts in one CameraManagerGlobal
 * instance and filters by deviceId afterwards. The visibility quota therefore applies independently
 * to each deviceId. Older Android releases have no deviceId and naturally use the default group.
 */
internal class CameraVisibilityLedger {
    private val statuses = LinkedHashMap<CameraVisibilityKey, Int>()
    private var configuredLimit: Int? = null
    private var visible = linkedSetOf<CameraVisibilityKey>()

    fun initialize(
        entries: Iterable<CameraVisibilityStatus>,
        limit: Int?,
    ): CameraVisibilityDelta {
        statuses.clear()
        entries.forEach { entry -> statuses[entry.key] = entry.status }
        configuredLimit = limit
        return recompute()
    }

    fun updateLimit(limit: Int?): CameraVisibilityDelta {
        configuredLimit = limit
        return recompute()
    }

    fun updateStatus(
        key: CameraVisibilityKey,
        status: Int,
    ): CameraVisibilityDelta {
        statuses[key] = status
        return recompute()
    }

    fun statusFor(key: CameraVisibilityKey): Int? = statuses[key]

    fun isVisible(key: CameraVisibilityKey): Boolean = key in visible

    fun visibleSnapshot(): Set<CameraVisibilityKey> = visible.toSet()

    private fun recompute(): CameraVisibilityDelta {
        val previous = visible
        val next = selectVisibleCameraKeys(statuses, configuredLimit)
        visible = next

        val hidden = previous.filterTo(linkedSetOf()) { it !in next }
        val shown =
            next.asSequence()
                .filter { it !in previous }
                .mapNotNull { key -> statuses[key]?.let { status -> CameraVisibilityStatus(key, status) } }
                .toList()
        return CameraVisibilityDelta(hidden, shown, next.toSet())
    }
}

internal fun selectVisibleCameraKeys(
    statuses: Map<CameraVisibilityKey, Int>,
    configuredLimit: Int?,
): LinkedHashSet<CameraVisibilityKey> {
    val result = linkedSetOf<CameraVisibilityKey>()
    val limit = configuredLimit?.coerceIn(0, MAX_VISIBLE_CAMERA_COUNT)
    val selectedPerDevice = HashMap<Int, Int>()

    statuses.forEach { (key, status) ->
        if (!isDiscoverableCameraStatus(status)) return@forEach
        if (limit == null) {
            result += key
            return@forEach
        }
        val selected = selectedPerDevice[key.deviceId] ?: 0
        if (selected < limit) {
            result += key
            selectedPerDevice[key.deviceId] = selected + 1
        }
    }
    return result
}
