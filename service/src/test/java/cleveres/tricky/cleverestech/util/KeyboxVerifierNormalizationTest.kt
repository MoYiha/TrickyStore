package cleveres.tricky.cleverestech.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import cleveres.tricky.cleverestech.util.KeyboxVerifier

class KeyboxVerifierNormalizationTest {

    private fun parseEntries(vararg keys: String): Set<String> {
        val entriesJson = keys.joinToString(", ") { "\"$it\": \"\"" }
        val json = """{"entries": {$entriesJson}}"""
        return KeyboxVerifier.parseCrl(StringReader(json))
    }

    @Test
    fun testNormalizeEntry_emptyKey() {
        val result = parseEntries("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testNormalizeEntry_validHex() {
        val hex32 = "a".repeat(32)
        val hex40 = "b".repeat(40)
        val hex64 = "c".repeat(64)

        val result = parseEntries(hex32, hex40, hex64)
        assertEquals(3, result.size)
        assertTrue(result.contains(hex32))
        assertTrue(result.contains(hex40))
        assertTrue(result.contains(hex64))
    }

    @Test
    fun testNormalizeEntry_validDecimal() {
        val result = parseEntries("255")

        assertTrue(result.contains("ff"))
        assertTrue(result.contains("0".repeat(30) + "ff"))
        assertTrue(result.contains("0".repeat(38) + "ff"))
        assertTrue(result.contains("0".repeat(62) + "ff"))
    }

    @Test
    fun testNormalizeEntry_negativeDecimal() {
        val result = parseEntries("-255")
        assertTrue(result.contains("-ff"))
        assertEquals(1, result.size)
    }

    @Test
    fun testNormalizeEntry_hexFallback() {
        val result = parseEntries("ab")
        assertEquals(1, result.size)
        assertTrue(result.contains("ab"))
    }

    @Test
    fun testNormalizeEntry_nonHexFallback() {
        val result = parseEntries("xyz")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testNormalizeEntry_hexWithUppercase() {
        val result = parseEntries("AB")
        assertTrue(result.contains("ab"))
    }

    @Test
    fun testNormalizeEntry_decimalLeadingZeros() {
        val result = parseEntries("0255")
        assertTrue(result.contains("255"))
    }
}
