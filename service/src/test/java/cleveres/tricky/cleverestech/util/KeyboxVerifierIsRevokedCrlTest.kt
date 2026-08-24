package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CrlBackend
import cleveres.tricky.cleverestech.CrlWire
import cleveres.tricky.cleverestech.RustBackendUnavailableException
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigInteger
import java.security.PublicKey

class KeyboxVerifierIsRevokedCrlTest {

    private val validCert = mock(X509Certificate::class.java)
    private val revokedCert = mock(X509Certificate::class.java)
    private val publicKey = mock(PublicKey::class.java)

    init {
        `when`(validCert.serialNumber).thenReturn(BigInteger("1"))
        `when`(revokedCert.serialNumber).thenReturn(BigInteger("2"))

        `when`(publicKey.encoded).thenReturn(byteArrayOf(1, 2, 3))

        `when`(validCert.publicKey).thenReturn(publicKey)
        `when`(revokedCert.publicKey).thenReturn(publicKey)
    }

    @Before
    fun setUp() {
        CrlBackend.queryOverride = null
    }

    @After
    fun tearDown() {
        CrlBackend.queryOverride = null
    }

    @Test
    fun testIsRevokedReturnsTrueForRevokedCert() {
        val crlHandle = CrlWire.Handle(1L, 1, 1)

        CrlBackend.queryOverride = { generation, queries ->
            require(generation == 1L)
            CrlWire.Result(generation, BooleanArray(queries.size) { true })
        }

        assertTrue(KeyboxVerifier.isRevoked(revokedCert, crlHandle))
    }

    @Test
    fun testIsRevokedReturnsFalseForValidCert() {
        val crlHandle = CrlWire.Handle(1L, 1, 1)

        CrlBackend.queryOverride = { generation, queries ->
            require(generation == 1L)
            CrlWire.Result(generation, BooleanArray(queries.size) { false })
        }

        assertFalse(KeyboxVerifier.isRevoked(validCert, crlHandle))
    }

    @Test
    fun testIsRevokedThrowsRustBackendUnavailableExceptionWhenCrlBackendReturnsNull() {
        val crlHandle = CrlWire.Handle(1L, 1, 1)

        CrlBackend.queryOverride = { _, _ -> null }

        var exceptionThrown = false
        try {
            KeyboxVerifier.isRevoked(validCert, crlHandle)
        } catch (e: RustBackendUnavailableException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun testIsRevokedReturnsFalseWhenPublicKeyEncodedIsNull() {
        val crlHandle = CrlWire.Handle(1L, 1, 1)
        val certWithNullKey = mock(X509Certificate::class.java)
        val nullPublicKey = mock(PublicKey::class.java)

        `when`(certWithNullKey.serialNumber).thenReturn(BigInteger("3"))
        `when`(nullPublicKey.encoded).thenReturn(null)
        `when`(certWithNullKey.publicKey).thenReturn(nullPublicKey)

        assertFalse(KeyboxVerifier.isRevoked(certWithNullKey, crlHandle))
    }
}
