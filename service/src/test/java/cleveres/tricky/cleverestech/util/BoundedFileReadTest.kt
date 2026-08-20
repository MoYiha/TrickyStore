package cleveres.tricky.cleverestech.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files

class BoundedFileReadTest {
    @Test
    fun `stable bounded snapshot is accepted`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val channel = GrowingChannel(bytes, bytes.size.toLong())

        assertArrayEquals(bytes, readChannelSnapshotBounded(channel, 1, 4))
    }

    @Test
    fun `read rejects channel that grows after declared size`() {
        val channel = GrowingChannel(byteArrayOf(1, 2, 3, 4, 5, 6), 4)

        assertThrows(IOException::class.java) {
            readChannelSnapshotBounded(channel, 1, 4)
        }
    }

    @Test
    fun `digest rejects channel that grows after declared size`() {
        val channel = GrowingChannel(byteArrayOf(1, 2, 3, 4, 5, 6), 4)

        assertThrows(IOException::class.java) {
            sha256ChannelSnapshotBounded(channel, 1, 4)
        }
    }

    @Test
    fun `utf8 snapshot decodes accepted bytes without reopening`() {
        val file = Files.createTempFile("bounded-utf8", ".txt").toFile()
        try {
            file.writeText("alpha\nbeta\n", Charsets.UTF_8)
            assertEquals("alpha\nbeta\n", readUtf8FileSnapshotBounded(file, 1, 64))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `utf8 snapshot rejects malformed input`() {
        val file = Files.createTempFile("bounded-utf8-invalid", ".txt").toFile()
        try {
            file.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
            assertThrows(IOException::class.java) {
                readUtf8FileSnapshotBounded(file, 1, 64)
            }
        } finally {
            file.delete()
        }
    }

    private class GrowingChannel(
        private val content: ByteArray,
        private val declaredSize: Long,
    ) : SeekableByteChannel {
        private var open = true
        private var offset = 0

        override fun read(dst: ByteBuffer): Int {
            check(open)
            if (offset >= content.size) return -1
            val count = minOf(dst.remaining(), content.size - offset)
            dst.put(content, offset, count)
            offset += count
            return count
        }

        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException()

        override fun position(): Long = offset.toLong()

        override fun position(newPosition: Long): SeekableByteChannel = throw UnsupportedOperationException()

        override fun size(): Long = if (offset < declaredSize) declaredSize else content.size.toLong()

        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException()

        override fun isOpen(): Boolean = open

        override fun close() {
            open = false
        }
    }
}
