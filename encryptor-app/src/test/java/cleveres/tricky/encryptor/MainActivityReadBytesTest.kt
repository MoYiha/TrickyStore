package cleveres.tricky.encryptor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class MainActivityReadBytesTest {
    @Test
    fun `readBytes preserves content across buffer growth`() {
        val input = ByteArray(96 * 1024) { index -> (index and 0xff).toByte() }

        assertArrayEquals(input, readBytes(ByteArrayInputStream(input)))
    }

    @Test
    fun `readBytes rejects content above the bounded XML limit`() {
        val oversized = ByteArray(10 * 1024 * 1024 + 1)

        assertThrows(IOException::class.java) {
            readBytes(ByteArrayInputStream(oversized))
        }
    }
}
