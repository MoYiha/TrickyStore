package cleveres.tricky.cleverestech.util

import org.junit.Test
import org.junit.Assert.*

class KeyboxVerifierReproTest {
    @Test
    fun testAmbiguousNumericStringInCrl() {
        // "123456" is a valid hex string (digits only).
        // If the CRL contains "123456", and it represents a HEX serial number,
        // the verifier MUST detect it.
        // Current implementation treats "123456" as Decimal -> 1E240 (Hex).
        // So it fails to revoke a certificate with actual serial "123456".

        val json = """
        {
          "entries": {
            "123456" : {
              "status": "REVOKED"
            }
          }
        }
        """.trimIndent()

        val revoked = KeyboxVerifier.parseCrl(json)

        // If "123456" was meant as Hex, the set should contain "123456".
        // If "123456" was meant as Decimal, the set should contain "1e240".
        // To be safe, we should probably support "123456" being in the set if it's a valid hex.

        assertTrue("Set should contain '123456' (treating numeric string as potential Hex)", revoked.contains("123456"))
    }
}
