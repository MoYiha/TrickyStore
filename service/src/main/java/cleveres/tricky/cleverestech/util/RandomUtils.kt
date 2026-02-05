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
        val sb = StringBuilder(prefix)
        while (sb.length < length - 1) {
            sb.append(Random.nextInt(10))
        }
        val digits = sb.toString().map { it.toString().toInt() }.toIntArray()
        var sum = 0
        var isSecond = true
        for (i in digits.indices.reversed()) {
            var d = digits[i]
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
        return (1..length)
            .map { CHAR_POOL[Random.nextInt(CHAR_POOL.length)] }
            .joinToString("")
    }

    fun generateRandomMac(): String {
        return (1..6)
            .map {
                val b = Random.nextInt(256)
                String.format("%02x", b)
            }
            .joinToString(":")
    }

    fun generateRandomAndroidId(): String {
        return (1..16)
            .map { HEX_POOL[Random.nextInt(HEX_POOL.length)] }
            .joinToString("")
    }

    fun generateRandomSimIso(): String {
        return COUNTRIES.random()
    }

    fun generateRandomCarrier(): String {
        return CARRIERS.random()
    }
}
