package cleveres.tricky.cleverestech.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxVerifierIsHexTest {

    @Test
    fun `isHex returns true for valid lowercase hex strings`() {
        assertTrue(KeyboxVerifier.isHex("0123456789abcdef"))
        assertTrue(KeyboxVerifier.isHex("a"))
        assertTrue(KeyboxVerifier.isHex("f"))
        assertTrue(KeyboxVerifier.isHex("0"))
        assertTrue(KeyboxVerifier.isHex("9"))
    }

    @Test
    fun `isHex returns true for valid uppercase hex strings`() {
        assertTrue(KeyboxVerifier.isHex("0123456789ABCDEF"))
        assertTrue(KeyboxVerifier.isHex("A"))
        assertTrue(KeyboxVerifier.isHex("F"))
    }

    @Test
    fun `isHex returns true for mixed case hex strings`() {
        assertTrue(KeyboxVerifier.isHex("aBcD1234eF"))
    }

    @Test
    fun `isHex returns false for empty strings`() {
        assertFalse(KeyboxVerifier.isHex(""))
    }

    @Test
    fun `isHex returns false for strings with invalid characters`() {
        assertFalse(KeyboxVerifier.isHex("g"))
        assertFalse(KeyboxVerifier.isHex("G"))
        assertFalse(KeyboxVerifier.isHex("123g456"))
        assertFalse(KeyboxVerifier.isHex("abcdefg"))
        assertFalse(KeyboxVerifier.isHex(" "))
        assertFalse(KeyboxVerifier.isHex("123 456"))
        assertFalse(KeyboxVerifier.isHex("-1"))
        assertFalse(KeyboxVerifier.isHex("!@#$"))
    }
}
