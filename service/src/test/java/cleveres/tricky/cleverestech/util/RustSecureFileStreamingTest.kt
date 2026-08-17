package cleveres.tricky.cleverestech.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RustSecureFileStreamingTest {
    @Test
    fun `declared streaming supports zero one oneMiB tenMiB and maximum`() {
        for (size in listOf(0, 1, 1024 * 1024, 10 * 1024 * 1024, MAX_FILE_BYTES)) {
            val input = ByteArray(size) { index -> (index * 31).toByte() }
            val output = ByteArrayOutputStream(size)
            val scratch = ByteArray(64 * 1024)

            copyDeclaredBody(ByteArrayInputStream(input), output, size, scratch)

            assertArrayEquals(input, output.toByteArray())
            assertTrue(scratch.all { it == 0.toByte() })
        }
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

    private companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}
