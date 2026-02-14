import org.json.JSONObject
import java.math.BigInteger
import java.util.HashSet

fun main() {
    val ambiguousKey = "11112222333344445555666677778888"
    val jsonStr = """
    {
      "entries": {
        "$ambiguousKey": "REVOKED"
      }
    }
    """.trimIndent()

    val json = JSONObject(jsonStr)
    val entries = json.getJSONObject("entries")
    val set = HashSet<String>()
    val keys = entries.keys()

    while (keys.hasNext()) {
        val decStr = keys.next()
        println("Processing key: '$decStr' length: ${decStr.length}")

        var added = false
        try {
            if (decStr.length > 1 && decStr.startsWith("0")) {
                throw NumberFormatException("Leading zero implies Hex")
            }
            val hexStr = BigInteger(decStr).toString(16).lowercase()
            println("Parsed as decimal -> hex: $hexStr")
            set.add(hexStr)
            added = true
        } catch (e: Exception) {
            println("Not a decimal: ${e.message}")
        }

        // Ambiguity handling (mirrors KeyboxVerifier logic)
        if (decStr.length == 32 || decStr.length == 40 || decStr.length == 64) {
            if (decStr.matches(Regex("^[0-9a-fA-F]+$"))) {
                println("Ambiguity detected. Adding literal: $decStr")
                set.add(decStr.lowercase())
            }
        }
    }

    if (set.contains(ambiguousKey)) {
        println("SUCCESS: Set contains literal key")
    } else {
        println("FAILURE: Set does NOT contain literal key")
    }
}
