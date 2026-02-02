package cleveres.tricky.cleverestech.util

import org.junit.Test
import org.junit.Assert.*

class KeyboxVerifierTest {
    @Test
    fun testParseCrl_RealFormat() {
        // Sample from https://android.googleapis.com/attestation/status
        // entries is a Map/Object, not an Array
        val json = """
        {
          "entries": {
            "6681152659205225093" : {
              "status": "REVOKED",
              "reason": "KEY_COMPROMISE"
            },
            "12345" : {
              "status": "REVOKED"
            }
          }
        }
        """.trimIndent()

        val revoked = KeyboxVerifier.parseCrl(json)

        // 6681152659205225093 (dec) -> 5cb838f1fe157a85 (hex)
        // 12345 (dec) -> 3039 (hex)

        assertTrue("Should contain 5cb838f1fe157a85 but got $revoked", revoked.contains("5cb838f1fe157a85"))
        assertTrue("Should contain 3039 but got $revoked", revoked.contains("3039"))
    }

    @Test
    fun testParseCrl_WithHexStrings() {
        // CRL containing both Decimal and Hex keys
        // "6681152659205225093" -> 5cb838f1fe157a85 (Decimal)
        // "c35747a084470c3135aeefe2b8d40cd6" -> (Hex)
        val json = """
        {
          "entries": {
            "6681152659205225093" : {
              "status": "REVOKED"
            },
            "c35747a084470c3135aeefe2b8d40cd6" : {
              "status": "REVOKED"
            }
          }
        }
        """.trimIndent()

        val revoked = KeyboxVerifier.parseCrl(json)

        assertTrue("Should contain decimal-converted key", revoked.contains("5cb838f1fe157a85"))
        assertTrue("Should contain hex key", revoked.contains("c35747a084470c3135aeefe2b8d40cd6"))
    }
}
