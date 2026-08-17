package cleveres.tricky.cleverestech

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxWireTest {
    @Test
    fun `valid DER response decodes and wipes transport bytes`() {
        val privateKey = byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01)
        val response = encodeResponse("ecdsa", privateKey, listOf("LEAF", "ROOT"))
        val decoded = KeyboxWire.decode(response)

        requireNotNull(decoded)
        assertEquals(1, decoded.declaredKeyboxes)
        assertEquals(1, decoded.keyboxCount)
        assertEquals(1, decoded.keys.size)
        assertEquals("ecdsa", decoded.keys[0].algorithm)
        assertArrayEquals(privateKey, decoded.keys[0].privateKeyPkcs8)
        assertEquals(listOf("LEAF", "ROOT"), decoded.keys[0].certificatesPem)
        assertTrue(response.all { it == 0.toByte() })

        decoded.wipePrivateKeys()
        assertTrue(decoded.keys[0].privateKeyPkcs8.all { it == 0.toByte() })
    }

    @Test
    fun `legacy wire version fails closed`() {
        val response = encodeResponse("EC", byteArrayOf(0x30, 1), listOf("CERT"))
        response[0] = 1

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `unsupported algorithm fails closed`() {
        val response = encodeResponse("Ed25519", byteArrayOf(0x30, 1), listOf("CERT"))

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `trailing bytes fail closed and transport is wiped`() {
        val valid = encodeResponse("RSA", byteArrayOf(0x30, 1), listOf("CERT"))
        val response = valid + byteArrayOf(1)

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `invalid certificate utf8 fails closed and private copy is not returned`() {
        val response =
            encodeRawResponse(
                algorithm = "EC".toByteArray(),
                privateKey = byteArrayOf(0x30, 1),
                certificates = listOf(byteArrayOf(0xc3.toByte(), 0x28)),
            )

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `declared keybox count must match structural counts`() {
        val response = encodeResponse("EC", byteArrayOf(0x30, 1), listOf("CERT"))
        response[1] = 2

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `truncated length delimited fields fail closed`() {
        val response = encodeResponse("EC", byteArrayOf(0x30, 1), listOf("CERT")).copyOf(12)

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    private fun encodeResponse(
        algorithm: String,
        privateKey: ByteArray,
        certificates: List<String>,
    ): ByteArray =
        encodeRawResponse(
            algorithm.toByteArray(),
            privateKey,
            certificates.map { it.toByteArray() },
        )

    private fun encodeRawResponse(
        algorithm: ByteArray,
        privateKey: ByteArray,
        certificates: List<ByteArray>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(2)
            data.writeByte(1)
            data.writeByte(1)
            data.writeShort(1)
            data.writeByte(algorithm.size)
            data.writeByte(certificates.size)
            data.writeInt(privateKey.size)
            data.write(algorithm)
            data.write(privateKey)
            for (certificate in certificates) {
                data.writeInt(certificate.size)
                data.write(certificate)
            }
        }
        return output.toByteArray()
    }
}
