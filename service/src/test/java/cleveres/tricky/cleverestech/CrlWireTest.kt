package cleveres.tricky.cleverestech

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrlWireTest {
    @Test
    fun `refresh encoder preserves binary CRL bytes`() {
        val crl = byteArrayOf('{'.code.toByte(), '}'.code.toByte())
        val output = ByteArrayOutputStream()

        CrlWire.writeRefresh(output, crl)

        val encoded = output.toByteArray()
        assertEquals(encoded.size, CrlWire.refreshLength(crl.size))
        assertArrayEquals(byteArrayOf(2, 1, 0, 0, 0, 2), encoded.copyOfRange(0, 6))
        assertArrayEquals(crl, encoded.copyOfRange(6, encoded.size))
    }

    @Test
    fun `query encoder preserves generation serial and SPKI bytes`() {
        val serial = byteArrayOf(0xff.toByte(), 0x01)
        val spki = byteArrayOf(0x30, 0x01, 0x00)
        val queries = listOf(CrlWire.Query(serial, spki))
        val output = ByteArrayOutputStream()

        CrlWire.writeQuery(output, 7, queries)

        val encoded = output.toByteArray()
        assertEquals(encoded.size, CrlWire.queryLength(queries))
        assertArrayEquals(byteArrayOf(2, 2), encoded.copyOfRange(0, 2))
        assertEquals(7L, readU64(encoded, 2))
        assertArrayEquals(byteArrayOf(0, 1, 0, 2, 0, 0, 0, 3), encoded.copyOfRange(10, 18))
        assertArrayEquals(serial, encoded.copyOfRange(18, 20))
        assertArrayEquals(spki, encoded.copyOfRange(20, 23))
    }

    @Test
    fun `refresh decoder returns opaque counts and wipes transport`() {
        val response = refreshResponse(generation = 9, rawCount = 7, normalizedCount = 11)
        val decoded = CrlWire.decodeRefresh(response)

        requireNotNull(decoded)
        assertEquals(9, decoded.generation)
        assertEquals(7, decoded.rawEntryCount)
        assertEquals(11, decoded.normalizedEntryCount)
        assertTrue(response.all { it == 0.toByte() })
    }

    @Test
    fun `query decoder returns bitset and rejects generation count and unused bit ambiguity`() {
        val response = queryResponse(9, 9, byteArrayOf(0b00000101, 0b00000001))
        val decoded = CrlWire.decodeQuery(response, 9, 9)

        requireNotNull(decoded)
        assertEquals(9, decoded.generation)
        assertTrue(decoded.revoked[0])
        assertEquals(false, decoded.revoked[1])
        assertTrue(decoded.revoked[2])
        assertTrue(decoded.revoked[8])
        assertTrue(response.all { it == 0.toByte() })

        val wrongGeneration = queryResponse(8, 1, byteArrayOf(0))
        assertNull(CrlWire.decodeQuery(wrongGeneration, 9, 1))
        assertTrue(wrongGeneration.all { it == 0.toByte() })

        val wrongCount = queryResponse(9, 2, byteArrayOf(0))
        assertNull(CrlWire.decodeQuery(wrongCount, 9, 1))
        assertTrue(wrongCount.all { it == 0.toByte() })

        val unusedBit = queryResponse(9, 1, byteArrayOf(0b10000000.toByte()))
        assertNull(CrlWire.decodeQuery(unusedBit, 9, 1))
        assertTrue(unusedBit.all { it == 0.toByte() })
    }

    @Test
    fun `bounds reject oversized refresh query count serial and SPKI`() {
        assertNull(CrlWire.refreshLength(CrlWire.MAX_CRL_BYTES + 1))
        assertNull(
            CrlWire.queryLength(
                List(CrlWire.MAX_QUERY_COUNT + 1) { CrlWire.Query(byteArrayOf(1), byteArrayOf(1)) },
            ),
        )
        assertNull(
            CrlWire.queryLength(
                listOf(CrlWire.Query(ByteArray(CrlWire.MAX_SERIAL_BYTES + 1), byteArrayOf(1))),
            ),
        )
        assertNull(
            CrlWire.queryLength(
                listOf(CrlWire.Query(byteArrayOf(1), ByteArray(CrlWire.MAX_SPKI_BYTES + 1))),
            ),
        )
    }

    private fun refreshResponse(
        generation: Long,
        rawCount: Int,
        normalizedCount: Int,
    ): ByteArray = ByteArray(18).also { output ->
        output[0] = 2
        output[1] = 1
        writeU64(output, 2, generation)
        writeU32(output, 10, rawCount)
        writeU32(output, 14, normalizedCount)
    }

    private fun queryResponse(
        generation: Long,
        queryCount: Int,
        bits: ByteArray,
    ): ByteArray = ByteArray(12 + bits.size).also { output ->
        output[0] = 2
        output[1] = 2
        writeU64(output, 2, generation)
        output[10] = (queryCount ushr 8).toByte()
        output[11] = queryCount.toByte()
        bits.copyInto(output, 12)
    }

    private fun readU64(
        input: ByteArray,
        offset: Int,
    ): Long {
        var value = 0L
        repeat(8) { index -> value = (value shl 8) or (input[offset + index].toLong() and 0xffL) }
        return value
    }

    private fun writeU32(
        output: ByteArray,
        offset: Int,
        value: Int,
    ) {
        output[offset] = (value ushr 24).toByte()
        output[offset + 1] = (value ushr 16).toByte()
        output[offset + 2] = (value ushr 8).toByte()
        output[offset + 3] = value.toByte()
    }

    private fun writeU64(
        output: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (index in 0 until 8) {
            output[offset + index] = (value ushr (56 - index * 8)).toByte()
        }
    }
}
