package cleveres.tricky.cleverestech.util

import kotlin.random.Random

object RandomUtils {

    private const val CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val HEX_POOL = "0123456789abcdef"

    // Simple list for random selection
    private val COUNTRIES = listOf("us", "uk", "de", "fr", "es", "it", "ca", "au", "jp", "kr", "cn", "in", "br", "ru")
    private val CARRIERS = listOf(
        "T-Mobile", "Verizon", "AT&T", "Vodafone", "O2", "Orange", "Telekom",
        "Movistar", "TIM", "Rogers", "Telstra", "SoftBank", "Docomo", "China Mobile", "Jio", "Vivo"
    )

    fun generateLuhn(length: Int, prefix: String = ""): String {
        // Optimization: Use StringBuilder directly to avoid intermediate String and List allocations
        val sb = StringBuilder(length)
        sb.append(prefix)
        while (sb.length < length - 1) {
            sb.append(Random.nextInt(10))
        }

        var sum = 0
        var isSecond = true
        // Optimization: Iterate over characters in StringBuilder instead of converting to IntArray
        for (i in sb.length - 1 downTo 0) {
            var d = sb[i] - '0'
            if (isSecond) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            isSecond = !isSecond
        }
        val checkDigit = (10 - (sum % 10)) % 10
        sb.append(checkDigit)
        return sb.toString()
    }

    fun generateRandomSerial(length: Int): String {
        // Optimization: Use StringBuilder loop instead of map + joinToString
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(CHAR_POOL[Random.nextInt(CHAR_POOL.length)])
        }
        return sb.toString()
    }

    fun generateRandomMac(): String {
        // Optimization: Use StringBuilder and manual hex formatting
        val sb = StringBuilder(17)
        for (i in 0 until 6) {
            if (i > 0) sb.append(':')
            val b = Random.nextInt(256)
            val high = (b shr 4) and 0xF
            val low = b and 0xF
            sb.append(HEX_POOL[high])
            sb.append(HEX_POOL[low])
        }
        return sb.toString()
    }

    fun generateRandomAndroidId(): String {
        // Optimization: Use StringBuilder loop
        val sb = StringBuilder(16)
        repeat(16) {
            sb.append(HEX_POOL[Random.nextInt(HEX_POOL.length)])
        }
        return sb.toString()
    }

    fun generateRandomSimIso(): String {
        return COUNTRIES.random()
    }

    fun generateRandomCarrier(): String {
        return CARRIERS.random()
    }
}
