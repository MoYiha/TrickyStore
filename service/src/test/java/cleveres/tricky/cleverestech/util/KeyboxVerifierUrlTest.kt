package cleveres.tricky.cleverestech.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxVerifierUrlTest {

    @Test
    fun `isAllowedCrlUrl allows valid https urls`() {
        assertTrue(KeyboxVerifier.isAllowedCrlUrl("https://android.googleapis.com/attestation/status", false))
        assertTrue(KeyboxVerifier.isAllowedCrlUrl("https://example.com/crl", true))
        assertTrue(KeyboxVerifier.isAllowedCrlUrl("HTTPS://EXAMPLE.COM/crl", false))
    }

    @Test
    fun `isAllowedCrlUrl rejects relative urls`() {
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("/attestation/status", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("attestation/status", true))
    }

    @Test
    fun `isAllowedCrlUrl rejects urls with user info`() {
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("https://user:pass@example.com/crl", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("https://user@example.com/crl", true))
    }

    @Test
    fun `isAllowedCrlUrl rejects urls with fragments`() {
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("https://example.com/crl#fragment", false))
    }

    @Test
    fun `isAllowedCrlUrl rejects http urls when allowLoopbackHttp is false`() {
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("http://example.com/crl", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("http://localhost/crl", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("http://127.0.0.1/crl", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("http://[::1]/crl", false))
    }

    @Test
    fun `isAllowedCrlUrl rejects non-loopback http urls when allowLoopbackHttp is true`() {
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("http://example.com/crl", true))
    }

    @Test
    fun `isAllowedCrlUrl allows loopback http urls when allowLoopbackHttp is true`() {
        assertTrue(KeyboxVerifier.isAllowedCrlUrl("http://localhost/crl", true))
        assertTrue(KeyboxVerifier.isAllowedCrlUrl("http://127.0.0.1/crl", true))
        // Note: The loopback check in URI uses "[::1]" for IPv6 localhost, but the code checks `uri.host == "::1"`.
        // Java's URI.getHost() strips the brackets, so "[::1]" results in host "::1".
        assertTrue(KeyboxVerifier.isAllowedCrlUrl("http://[::1]/crl", true))
        assertTrue(KeyboxVerifier.isAllowedCrlUrl("HTTP://LOCALHOST/crl", true))
    }

    @Test
    fun `isAllowedCrlUrl rejects malformed urls`() {
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("not a url", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("https://[invalid]/crl", false))
    }

    @Test
    fun `isAllowedCrlUrl rejects ftp and other schemes`() {
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("ftp://example.com/crl", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("file:///crl", false))
        assertFalse(KeyboxVerifier.isAllowedCrlUrl("data:,Hello%2C%20World!", false))
    }
}
