package cleveres.tricky.cleverestech

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedCboxBackendTest {
    @Test
    fun `multibyte author at producer UTF-16 limit decodes`() {
        val author = "é".repeat(MAX_AUTHOR_UTF16_UNITS)
        assertTrue(author.toByteArray(Charsets.UTF_8).size > MAX_AUTHOR_UTF16_UNITS)
        val response = encodeResponse(author)

        val payload = requireNotNull(FusedCboxBackend.decode(response))

        assertEquals(author, payload.author)
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `author over producer UTF-16 limit fails closed`() {
        val response = encodeResponse("a".repeat(MAX_AUTHOR_UTF16_UNITS + 1))

        assertNull(FusedCboxBackend.decode(response))
        assertTrue(response.all { it == 0.toByte() })
    }

    private fun encodeResponse(author: String): ByteArray {
        val authorBytes = author.toByteArray(Charsets.UTF_8)
        val keyboxWire = encodeKeyboxWire()
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(authorBytes.size)
            data.writeInt(keyboxWire.size)
            data.writeByte(0)
            data.write(authorBytes)
            data.write(keyboxWire)
        }
        return output.toByteArray()
    }

    private fun encodeKeyboxWire(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(4)
            data.writeByte(1)
            data.writeByte(1)
            data.writeShort(1)
            data.write(ByteArray(32) { 0xab.toByte() })
            data.writeByte(2)
            data.writeByte(1)
            data.write(ByteArray(16) { index -> (index + 1).toByte() })
            data.writeBytes("EC")
            data.writeInt(1)
            data.writeByte(0x30)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_AUTHOR_UTF16_UNITS = 1024
    }
}
