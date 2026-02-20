package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.KeyboxVerifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReproKeyIDConfusionTest {

    @Test
    fun testAmbiguousKeyID() {
        // A 32-character string that happens to be all digits.
        // It is statistically possible for a Hex Key ID (MD5/128-bit) or truncated SHA to be all digits.
        // Google CRL uses Hex for Key IDs.
        // If "11112222333344445555666677778888" is in the CRL, it implies the Key with that ID is revoked.

        val ambiguousKey = "11112222333344445555666677778888"
        val json = """
        {
          "entries": {
            "$ambiguousKey": "REVOKED"
          }
        }
        """.trimIndent()

        val revoked = KeyboxVerifier.parseCrl(json)

        println("Ambiguous Key: $ambiguousKey")
        println("Revoked Set: $revoked")

        // We DO NOT expect the set to contain the key ITSELF (treated as Hex) if it is a valid decimal.
        // We prioritize avoiding false positives (revoking unrelated certs) over catching rare ambiguous Key IDs.
        assertFalse("Should NOT contain '$ambiguousKey' (Hex literal) to avoid False Positives", revoked.contains(ambiguousKey))

        // We DO expect the Decimal interpretation (ambiguity handling).
        val decimalInterpretation = java.math.BigInteger(ambiguousKey).toString(16).lowercase()
        assertTrue("Should contain '$decimalInterpretation' (Decimal interpretation)", revoked.contains(decimalInterpretation))
    }
}
