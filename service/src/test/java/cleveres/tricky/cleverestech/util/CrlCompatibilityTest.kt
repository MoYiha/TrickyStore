package cleveres.tricky.cleverestech.util

import java.io.IOException
import java.io.StringReader
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito

class CrlCompatibilityTest {
    @Test
    fun `production normalization matches frozen managed oracle`() {
        val json =
            """{"ignored":{"nested":[1,2,3]},"entries":{"1":"REVOKED","0001":{},"-2":null,"ABCDEF":"x","0000000000000000000000000000000A":[],"15":true,"ffffffffffffffffffffffffffffffffffffffff":"x"}}"""

        val expected = ManagedCrlOracle.parse(StringReader(json))
        val actual = KeyboxVerifier.parseCrl(json)

        assertEquals(expected, actual)
    }

    @Test
    fun `production normalization preserves decimal padding compatibility`() {
        val json = """{"entries":{"255":0}}"""
        val normalized = KeyboxVerifier.parseCrl(json)

        assertEquals(ManagedCrlOracle.parse(StringReader(json)), normalized)
        assertEquals(true, "ff" in normalized)
        assertEquals(true, "000000000000000000000000000000ff" in normalized)
        assertEquals(true, "00000000000000000000000000000000000000ff" in normalized)
        assertEquals(true, "00000000000000000000000000000000000000000000000000000000000000ff" in normalized)
    }

    @Test
    fun `production parser rejects the same structural failures as managed oracle`() {
        val oversized = "1".repeat(129)
        val invalid =
            listOf(
                "{}",
                "[]",
                """{"entries":[]}""",
                """{"entries":{"$oversized":0}}""",
            )

        for (json in invalid) {
            assertThrows(Exception::class.java) { ManagedCrlOracle.parse(StringReader(json)) }
            assertThrows(IOException::class.java) { KeyboxVerifier.parseCrl(json) }
        }
    }

    @Test
    fun `production revocation matching equals managed serial and digest oracle`() {
        val encoded = byteArrayOf(0, 1, 2, 3, 4, 5, 0x7f, 0x80.toByte(), 0xff.toByte())
        val certificate = mockCertificate(BigInteger("12345678901234567890"), encoded)
        val digestAlgorithms = listOf("SHA-1", "SHA-256", "MD5")

        val none = setOf("deadbeef")
        assertEquals(ManagedCrlOracle.isRevoked(certificate, none), KeyboxVerifier.isRevoked(certificate, none))

        val serial = setOf(certificate.serialNumber.toString(16))
        assertEquals(true, ManagedCrlOracle.isRevoked(certificate, serial))
        assertEquals(true, KeyboxVerifier.isRevoked(certificate, serial))

        for (algorithm in digestAlgorithms) {
            val digest = MessageDigest.getInstance(algorithm).digest(encoded).toHex()
            val revoked = setOf(digest)
            assertEquals(
                "digest compatibility for $algorithm",
                ManagedCrlOracle.isRevoked(certificate, revoked),
                KeyboxVerifier.isRevoked(certificate, revoked),
            )
        }
    }

    private fun mockCertificate(
        serial: BigInteger,
        encodedPublicKey: ByteArray,
    ): X509Certificate {
        val publicKey = Mockito.mock(PublicKey::class.java)
        Mockito.`when`(publicKey.encoded).thenReturn(encodedPublicKey)
        val certificate = Mockito.mock(X509Certificate::class.java)
        Mockito.`when`(certificate.serialNumber).thenReturn(serial)
        Mockito.`when`(certificate.publicKey).thenReturn(publicKey)
        return certificate
    }

    private fun ByteArray.toHex(): String =
        buildString(size * 2) {
            for (byte in this@toHex) append((byte.toInt() and 0xff).toString(16).padStart(2, '0'))
        }
}