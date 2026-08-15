package cleveres.tricky.cleverestech

internal const val MAX_VISIBLE_CAMERA_COUNT = 16

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
