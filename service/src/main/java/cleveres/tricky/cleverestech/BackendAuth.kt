package cleveres.tricky.cleverestech

internal object BackendAuth {
    const val ENV_NAME = "CLEVERES_TRICKY_BACKEND_AUTH"
    const val TOKEN_BYTES = 32
    private const val TOKEN_HEX_CHARS = TOKEN_BYTES * 2

    fun fromEnvironment(): ByteArray? = decodeHex(System.getenv(ENV_NAME))

    internal fun decodeHex(value: String?): ByteArray? {
        if (value == null || value.length != TOKEN_HEX_CHARS) return null
        val output = ByteArray(TOKEN_BYTES)
        for (index in 0 until TOKEN_BYTES) {
            val high = decodeNibble(value[index * 2]) ?: run {
                output.fill(0)
                return null
            }
            val low = decodeNibble(value[index * 2 + 1]) ?: run {
                output.fill(0)
                return null
            }
            output[index] = ((high shl 4) or low).toByte()
        }
        if (output.all { it == 0.toByte() }) {
            output.fill(0)
            return null
        }
        return output
    }

    private fun decodeNibble(value: Char): Int? =
        when (value) {
            in '0'..'9' -> value.code - '0'.code
            in 'a'..'f' -> value.code - 'a'.code + 10
            else -> null
        }
}
