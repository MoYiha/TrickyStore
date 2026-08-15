package cleveres.tricky.cleverestech

internal const val MAX_VISIBLE_SIM_COUNT = 8

internal fun boundedVisibleSubscriptionCount(
    realCount: Int,
    configuredLimit: Int?,
): Int {
    val real = realCount.coerceAtLeast(0)
    val limit = configuredLimit ?: return real
    return minOf(real, limit.coerceIn(0, MAX_VISIBLE_SIM_COUNT))
}

internal fun <T> boundedVisibleSubscriptions(
    realSubscriptions: List<T>,
    configuredLimit: Int?,
): List<T> {
    val limit = configuredLimit ?: return realSubscriptions
    val bounded = limit.coerceIn(0, MAX_VISIBLE_SIM_COUNT)
    if (realSubscriptions.size <= bounded) return realSubscriptions
    return realSubscriptions.subList(0, bounded)
}
