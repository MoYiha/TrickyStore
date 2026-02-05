package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.KeyboxVerifier
import org.junit.Assert.assertFalse
import org.junit.Test

class ReproFalsePositiveRevocationTest {

    @Test
    fun testAmbiguousKeyDoesNotProduceFalsePositive() {
        // A 32-character string that consists only of digits.
        // It is a valid Decimal number (Google CRL format).
        // It is ALSO a valid Hex string (if interpreted as Hex Key ID).
        //
        // Google CRL uses Decimal Serial Numbers.
        // We should interpret it as Decimal.
        // We should NOT interpret it as Hex, because that would revoke a completely different certificate
        // (one whose serial number in Hex matches this string).

        val decimalSerial = "10000000000000000000000000000001" // 32 chars

        val json = """
        {
          "entries": {
            "$decimalSerial": "REVOKED"
          }
        }
        """.trimIndent()

        val revoked = KeyboxVerifier.parseCrl(json)
        println("Revoked: $revoked")

        // The bug is that it adds "10000000000000000000000000000001" to the set
        assertFalse("Should NOT contain literal string '$decimalSerial' just because it looks like Hex", revoked.contains(decimalSerial.lowercase()))
    }
}
