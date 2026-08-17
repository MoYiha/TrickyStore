package cleveres.tricky.cleverestech

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrlWireTest {
    @Test
    fun `request encoder preserves binary CRL and certificate query bytes`() {
        val crl = byteArrayOf('{'.code.toByte(), '}'.code.toByte())
        val serial = byteArrayOf(0xff.toByte(), 0x01)
        val spki = byteArrayOf(0x30, 0x01, 0x00)
        val queries = listOf(CrlWire.Query(serial, spki))
        val output = ByteArrayOutputStream()

        CrlWire.writeRequest(output, crl, queries)

        val encoded = output.toByteArray()
        assertEquals(encoded.size, CrlWire.requestLength(crl.size, queries))
        assertArrayEquals(byteArrayOf(0, 0, 0, 2, 0, 1), encoded.copyOfRange(0, 6))
        assertArrayEquals(crl, encoded.copyOfRange(6, 8))
        assertArrayEquals(byteArrayOf(0, 2, 0, 0, 0, 3), encoded.copyOfRange(8, 14))
        assertArrayEquals(serial, encoded.copyOfRange(14, 16))
        assertArrayEquals(spki, encoded.copyOfRange(16, 19))
    }

    @Test
    fun `strict response decoder returns counts and revocation bitset and wipes transport`() {
        val response =
            response(
                rawCount = 7,
                normalizedCount = 11,
                queryCount = 9,
                bits = byteArrayOf(0b00000101, 0b00000001),
            )
        val decoded = CrlWire.decode(response, 9)

        requireNotNull(decoded)
        assertEquals(7, decoded.rawEntryCount)
        assertEquals(11, decoded.normalizedEntryCount)
        assertTrue(decoded.revoked[0])
        assertEquals(false, decoded.revoked[1])
        assertTrue(decoded.revoked[2])
        assertTrue(decoded.revoked[8])
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `decoder rejects version count length signed overflow and unused bit ambiguity`() {
        val wrongVersion = response(1, 1, 1, byteArrayOf(0)).also { it[0] = 2 }
        assertNull(CrlWire.decode(wrongVersion, 1))
        assertTrue(wrongVersion.all { it == 0.toByte() })

        val wrongCount = response(1, 1, 2, byteArrayOf(0))
        assertNull(CrlWire.decode(wrongCount, 1))
        assertTrue(wrongCount.all { it == 0.toByte() })

        val negativeCount = response(Int.MIN_VALUE, 1, 1, byteArrayOf(0))
        assertNull(CrlWire.decode(negativeCount, 1))
        assertTrue(negativeCount.all { it == 0.toByte() })

        val trailing = response(1, 1, 1, byteArrayOf(0)) + byteArrayOf(0)
        assertNull(CrlWire.decode(trailing, 1))
        assertTrue(trailing.all { it == 0.toByte() })

        val unusedBit = response(1, 1, 1, byteArrayOf(0b10000000.toByte()))
        assertNull(CrlWire.decode(unusedBit, 1))
        assertTrue(unusedBit.all { it == 0.toByte() })
    }

    @Test
    fun `request bounds reject oversized CRL query count serial and SPKI`() {
        assertNull(CrlWire.requestLength(CrlWire.MAX_CRL_BYTES + 1, emptyList()))
        assertNull(
            CrlWire.requestLength(
                1,
                List(CrlWire.MAX_QUERY_COUNT + 1) { CrlWire.Query(byteArrayOf(1), byteArrayOf(1)) },
            ),
        )
        assertNull(
            CrlWire.requestLength(
                1,
                listOf(CrlWire.Query(ByteArray(CrlWire.MAX_SERIAL_BYTES + 1), byteArrayOf(1))),
            ),
        )
        assertNull(
            CrlWire.requestLength(
                1,
                listOf(CrlWire.Query(byteArrayOf(1), ByteArray(CrlWire.MAX_SPKI_BYTES + 1))),
            ),
        )
    }

    private fun response(
        rawCount: Int,
        normalizedCount: Int,
        queryCount: Int,
        bits: ByteArray,
    ): ByteArray {
        val output = ByteArray(11 + bits.size)
        output[0] = 1
        writeI32(output, 1, rawCount)
        writeI32(output, 5, normalizedCount)
        output[9] = (queryCount ushr 8).toByte()
        output[10] = queryCount.toByte()
        bits.copyInto(output, 11)
        return output
    }

    private fun writeI32(
        output: ByteArray,
        offset: Int,
        value: Int,
    ) {
        output[offset] = (value ushr 24).toByte()
        output[offset + 1] = (value ushr 16).toByte()
        output[offset + 2] = (value ushr 8).toByte()
        output[offset + 3] = value.toByte()
    }
}
