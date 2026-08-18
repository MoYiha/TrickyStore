package cleveres.tricky.cleverestech.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RustSecureFileStreamingTest {
    @Test
    fun `declared streaming supports zero one oneMiB tenMiB and maximum`() {
        for (size in listOf(0, 1, 1024 * 1024, 10 * 1024 * 1024, MAX_FILE_BYTES)) {
            val input = ByteArray(size) { index -> (index * 31).toByte() }
            val output = ByteArrayOutputStream(size)
            val scratch = ByteArray(CHUNK_BYTES)

            copyDeclaredBody(ByteArrayInputStream(input), output, size, scratch)

            assertArrayEquals(input, output.toByteArray())
            assertTrue(scratch.all { it == 0.toByte() })
        }
    }

    @Test
    fun `maximum length streaming emits bounded chunks and exact bytes`() {
        for (size in listOf(0, 1, CHUNK_BYTES - 1, CHUNK_BYTES, CHUNK_BYTES + 1, 1024 * 1024)) {
            val input = ByteArray(size) { index -> (index * 17).toByte() }
            val output = ByteArrayOutputStream(size)
            val scratch = ByteArray(CHUNK_BYTES)
            var largestChunk = 0

            val total =
                streamBoundedChunks(ByteArrayInputStream(input), MAX_FILE_BYTES.toLong(), scratch) { bytes, count ->
                    largestChunk = maxOf(largestChunk, count)
                    output.write(bytes, 0, count)
                }

            assertEquals(size.toLong(), total)
            assertTrue(largestChunk <= CHUNK_BYTES)
            assertArrayEquals(input, output.toByteArray())
            assertTrue(scratch.all { it == 0.toByte() })
        }
    }

    @Test
    fun `maximum length streaming never emits a chunk crossing limit`() {
        val input = ByteArray(CHUNK_BYTES + 1) { 0x41 }
        val scratch = ByteArray(CHUNK_BYTES)
        var emitted = 0

        assertThrows(IOException::class.java) {
            streamBoundedChunks(ByteArrayInputStream(input), CHUNK_BYTES.toLong(), scratch) { _, count ->
                emitted += count
            }
        }

        assertEquals(CHUNK_BYTES, emitted)
        assertTrue(scratch.all { it == 0.toByte() })
    }

    @Test
    fun `maximum length streaming rejects data when limit is zero`() {
        val scratch = ByteArray(8) { 0x55 }
        var emitted = false
        assertThrows(IOException::class.java) {
            streamBoundedChunks(ByteArrayInputStream(byteArrayOf(1)), 0, scratch) { _, _ -> emitted = true }
        }
        assertTrue(!emitted)
        assertTrue(scratch.all { it == 0.toByte() })
    }

    @Test
    fun `early eof fails and wipes scratch`() {
        val scratch = ByteArray(8) { 0x55 }
        assertThrows(IOException::class.java) {
            copyDeclaredBody(ByteArrayInputStream(byteArrayOf(1, 2, 3)), ByteArrayOutputStream(), 4, scratch)
        }
        assertTrue(scratch.all { it == 0.toByte() })
    }

    @Test
    fun `one byte oversize fails before commit and wipes scratch`() {
        val scratch = ByteArray(8) { 0x55 }
        assertThrows(IOException::class.java) {
            copyDeclaredBody(
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                ByteArrayOutputStream(),
                4,
                scratch,
            )
        }
        assertTrue(scratch.all { it == 0.toByte() })
    }

    @Test
    fun `repeated zero reads are treated as stalled input`() {
        val scratch = ByteArray(8) { 0x55 }
        val stalled =
            object : InputStream() {
                override fun read(): Int = 0

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = 0
            }
        assertThrows(IOException::class.java) {
            copyDeclaredBody(stalled, ByteArrayOutputStream(), 1, scratch)
        }
        assertTrue(scratch.all { it == 0.toByte() })
    }

    @Test
    fun `maximum length streaming also fails on repeated zero reads`() {
        val scratch = ByteArray(8) { 0x55 }
        val stalled =
            object : InputStream() {
                override fun read(): Int = 0

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = 0
            }
        assertThrows(IOException::class.java) {
            streamBoundedChunks(stalled, 1, scratch) { _, _ -> error("must not emit") }
        }
        assertTrue(scratch.all { it == 0.toByte() })
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}
