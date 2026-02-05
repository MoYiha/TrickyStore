package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.KeyboxVerifier
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxVerifierBugTest {

    @Test
    fun testParseCrlWithLongAllDigitHex() {
        // A 32-character string that happens to be all digits.
        // In the context of CRL (mixed Decimal u64 / Hex u128), this should be treated as Hex.
        // If treated as Decimal, the value is wildly different.

        val targetHex = "10000000000000000000000000000001"
        val json = """
        {
          "entries": {
            "$targetHex": "REVOKED"
          }
        }
        """.trimIndent()

        val revoked = KeyboxVerifier.parseCrl(json)

        println("Revoked Set: $revoked")

        // We expect the set to contain the hex string itself (normalized)
        // Since input is already lowercase hex (and valid), result should be identical.
        assertTrue("Should contain '$targetHex' but has $revoked", revoked.contains(targetHex))
    }
}
