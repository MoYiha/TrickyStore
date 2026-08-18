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
    fun `valid public metadata response decodes and wipes transport bytes`() {
        val keyId = ByteArray(16) { index -> (index + 1).toByte() }
        val certificates = listOf(byteArrayOf(0x30, 1), byteArrayOf(0x30, 2))
        val response = encodeResponse("EC", keyId, certificates)
        val decoded = KeyboxWire.decode(response)

        requireNotNull(decoded)
        assertEquals(1, decoded.declaredKeyboxes)
        assertEquals(1, decoded.keyboxCount)
        assertEquals(1, decoded.keys.size)
        assertEquals("EC", decoded.keys[0].algorithm)
        assertArrayEquals(keyId, decoded.keys[0].keyId)
        assertEquals(2, decoded.keys[0].certificatesDer.size)
        assertArrayEquals(certificates[0], decoded.keys[0].certificatesDer[0])
        assertArrayEquals(certificates[1], decoded.keys[0].certificatesDer[1])
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `legacy private-key wire version fails closed`() {
        val response = encodeResponse("EC", validKeyId(), listOf(byteArrayOf(0x30, 1)))
        response[0] = 2

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `zero opaque key identifier fails closed`() {
        val response = encodeResponse("EC", ByteArray(16), listOf(byteArrayOf(0x30, 1)))

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `unsupported algorithm fails closed`() {
        val response = encodeResponse("Ed25519", validKeyId(), listOf(byteArrayOf(0x30, 1)))

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `trailing bytes fail closed and transport is wiped`() {
        val valid = encodeResponse("RSA", validKeyId(), listOf(byteArrayOf(0x30, 1)))
        val response = valid + byteArrayOf(1)

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `declared keybox count must match structural counts`() {
        val response = encodeResponse("EC", validKeyId(), listOf(byteArrayOf(0x30, 1)))
        response[1] = 2

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `truncated length delimited fields fail closed`() {
        val response = encodeResponse("EC", validKeyId(), listOf(byteArrayOf(0x30, 1))).copyOf(22)

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `oversized certificate length fails closed before allocation`() {
        val response = encodeResponse("EC", validKeyId(), listOf(byteArrayOf(0x30, 1)))
        val certificateLengthOffset = 5 + 2 + 16 + 2
        response[certificateLengthOffset] = 0x7f
        response[certificateLengthOffset + 1] = 0xff.toByte()
        response[certificateLengthOffset + 2] = 0xff.toByte()
        response[certificateLengthOffset + 3] = 0xff.toByte()

        assertNull(KeyboxWire.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    private fun validKeyId(): ByteArray = ByteArray(16) { index -> (0x40 + index).toByte() }

    private fun encodeResponse(
        algorithm: String,
        keyId: ByteArray,
        certificates: List<ByteArray>,
    ): ByteArray {
        require(keyId.size == 16)
        val algorithmBytes = algorithm.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(3)
            data.writeByte(1)
            data.writeByte(1)
            data.writeShort(1)
            data.writeByte(algorithmBytes.size)
            data.writeByte(certificates.size)
            data.write(keyId)
            data.write(algorithmBytes)
            for (certificate in certificates) {
                data.writeInt(certificate.size)
                data.write(certificate)
            }
        }
        return output.toByteArray()
    }
}
