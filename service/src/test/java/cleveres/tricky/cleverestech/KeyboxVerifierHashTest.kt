package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.cert.X509Certificate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxVerifierHashTest {
    @Test
    fun `legacy hash set compatibility matches certificate SPKI digest`() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/keybox/valid_ec.xml"))
        val keybox =
            InputStreamReader(stream, Charsets.UTF_8).use {
                CertHack.parseKeyboxXml(it, "valid_ec.xml").single()
            }
        val certificate = keybox.certificates().first() as X509Certificate
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
        val expectedHex = buildString(digest.size * 2) {
            for (byte in digest) append("%02x".format(byte.toInt() and 0xff))
        }
        digest.fill(0)

        assertTrue(KeyboxVerifier.isRevoked(certificate, setOf(expectedHex)))
        assertFalse(KeyboxVerifier.isRevoked(certificate, setOf("0".repeat(64))))
    }
}
