package cleveres.tricky.cleverestech.util

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Reads one bounded regular-file snapshot from a single open channel.
 *
 * The declared size is validated before allocation/work, every byte read is capped by that
 * declaration, and the channel is checked again before the snapshot is accepted. This prevents a
 * metadata pre-check from turning into an unbounded read when a file grows concurrently.
 */
@Throws(IOException::class)
internal fun readFileSnapshotBounded(
    file: File,
    minBytes: Long,
    maxBytes: Long,
): ByteArray =
    Files.newByteChannel(
        file.toPath(),
        StandardOpenOption.READ,
        LinkOption.NOFOLLOW_LINKS,
    ).use { channel ->
        readChannelSnapshotBounded(channel, minBytes, maxBytes)
    }

/** SHA-256 variant that keeps memory bounded while enforcing the same snapshot contract. */
@Throws(IOException::class)
internal fun sha256FileSnapshotBounded(
    file: File,
    minBytes: Long,
    maxBytes: Long,
): ByteArray =
    Files.newByteChannel(
        file.toPath(),
        StandardOpenOption.READ,
        LinkOption.NOFOLLOW_LINKS,
    ).use { channel ->
        sha256ChannelSnapshotBounded(channel, minBytes, maxBytes)
    }

@Throws(IOException::class)
internal fun readChannelSnapshotBounded(
    channel: SeekableByteChannel,
    minBytes: Long,
    maxBytes: Long,
): ByteArray {
    val declaredSize = validatedSnapshotSize(channel, minBytes, maxBytes)
    require(declaredSize <= Int.MAX_VALUE) { "Bounded snapshot is too large for a byte array" }
    val bytes = ByteArray(declaredSize.toInt())
    return try {
        readExactly(channel, ByteBuffer.wrap(bytes), declaredSize)
        verifySnapshotEnd(channel, declaredSize)
        bytes
    } catch (error: Throwable) {
        bytes.fill(0)
        throw error
    }
}

@Throws(IOException::class)
internal fun sha256ChannelSnapshotBounded(
    channel: SeekableByteChannel,
    minBytes: Long,
    maxBytes: Long,
): ByteArray {
    val declaredSize = validatedSnapshotSize(channel, minBytes, maxBytes)
    val digest = MessageDigest.getInstance("SHA-256")
    val scratch = ByteArray(DEFAULT_BUFFER_SIZE)
    try {
        var remaining = declaredSize
        var emptyReads = 0
        while (remaining > 0) {
            val count =
                channel.read(
                    ByteBuffer.wrap(
                        scratch,
                        0,
                        minOf(remaining, scratch.size.toLong()).toInt(),
                    ),
                )
            if (count < 0) throw IOException("File ended before its declared size")
            if (count == 0) {
                if (++emptyReads > MAX_EMPTY_READS) throw IOException("File read stalled")
                continue
            }
            emptyReads = 0
            digest.update(scratch, 0, count)
            scratch.fill(0, 0, count)
            remaining -= count.toLong()
        }
        verifySnapshotEnd(channel, declaredSize)
        return digest.digest()
    } finally {
        scratch.fill(0)
    }
}

@Throws(IOException::class)
private fun validatedSnapshotSize(
    channel: SeekableByteChannel,
    minBytes: Long,
    maxBytes: Long,
): Long {
    require(minBytes >= 0L) { "minBytes must be non-negative" }
    require(maxBytes >= minBytes) { "maxBytes must be at least minBytes" }
    val declaredSize = channel.size()
    if (declaredSize !in minBytes..maxBytes) {
        throw IOException("File size is outside the supported range")
    }
    return declaredSize
}

@Throws(IOException::class)
private fun readExactly(
    channel: SeekableByteChannel,
    target: ByteBuffer,
    declaredSize: Long,
) {
    var total = 0L
    var emptyReads = 0
    while (target.hasRemaining()) {
        val count = channel.read(target)
        if (count < 0) throw IOException("File ended before its declared size")
        if (count == 0) {
            if (++emptyReads > MAX_EMPTY_READS) throw IOException("File read stalled")
            continue
        }
        emptyReads = 0
        total += count.toLong()
        if (total > declaredSize) throw IOException("File exceeded its declared size")
    }
}

@Throws(IOException::class)
private fun verifySnapshotEnd(
    channel: SeekableByteChannel,
    declaredSize: Long,
) {
    if (channel.size() != declaredSize) throw IOException("File size changed while being read")

    val probeBytes = ByteArray(1)
    try {
        val probe = ByteBuffer.wrap(probeBytes)
        var emptyReads = 0
        while (true) {
            val count = channel.read(probe)
            if (count < 0) break
            if (count > 0) throw IOException("File grew while being read")
            if (++emptyReads > MAX_EMPTY_READS) throw IOException("File read stalled")
        }
    } finally {
        probeBytes.fill(0)
    }

    if (channel.size() != declaredSize) throw IOException("File size changed while being read")
}

private const val MAX_EMPTY_READS = 16
