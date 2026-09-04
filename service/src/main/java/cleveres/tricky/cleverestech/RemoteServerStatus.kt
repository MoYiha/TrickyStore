package cleveres.tricky.cleverestech

/**
 * Maps remote HTTP failures to short, user-facing status text for the WebUI.
 * Keep these messages bounded because ServerConfig.lastStatus is persisted and
 * intentionally limited to 128 characters.
 */
internal object RemoteServerStatus {
    fun fromHttp(
        code: Int,
        retryAfterHeader: String?,
    ): String =
        when (code) {
            400 -> "BAD_REQUEST: Remote Server rejected the request. Check the configured URL and filter."
            401 -> "AUTH_ERROR: API key is missing, invalid, or expired. Update the Remote Server API credentials."
            403 -> "ACCESS_DENIED: API key access denied or temporarily banned. Check provider access or ban status."
            429 -> rateLimitedStatus(retryAfterHeader)
            503 -> "SERVICE_UNAVAILABLE: Remote Server unavailable or no eligible keybox is currently available."
            else -> "HTTP_$code: Remote Server request failed."
        }

    private fun rateLimitedStatus(retryAfterHeader: String?): String {
        val retryAfter = retryAfterHeader?.trim()?.toLongOrNull()?.takeIf { it in 1..MAX_RETRY_AFTER_SECONDS }
        return if (retryAfter != null) {
            "RATE_LIMITED: Too many requests. Retry after $retryAfter seconds."
        } else {
            "RATE_LIMITED: Too many requests. Wait before refreshing again."
        }
    }

    private const val MAX_RETRY_AFTER_SECONDS = 31L * 24 * 60 * 60
}
