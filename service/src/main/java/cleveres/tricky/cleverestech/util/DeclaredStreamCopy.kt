package cleveres.tricky.cleverestech.util

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Exact-length streaming primitive used by atomic broker writes. */
@Throws(IOException::class)
internal fun copyDeclaredBody(
    input: InputStream,
    output: OutputStream,
    declaredBodyLength: Int,
    scratch: ByteArray,
) {
    require(declaredBodyLength >= 0)
    require(scratch.isNotEmpty())
    var remaining = declaredBodyLength
    var emptyReads = 0
    try {
        while (remaining > 0) {
            val count = input.read(scratch, 0, minOf(remaining, scratch.size))
            if (count < 0) throw IOException("Input stream ended before declared length")
            if (count == 0) {
                if (++emptyReads > MAX_EMPTY_READS) throw IOException("Input stream stalled")
                continue
            }
            emptyReads = 0
            output.write(scratch, 0, count)
            scratch.fill(0, 0, count)
            remaining -= count
        }
        if (input.read() >= 0) throw IOException("Input stream exceeds declared length")
    } finally {
        scratch.fill(0)
    }
}

/**
 * Maximum-length streaming primitive used by WebUI staging.
 *
 * Each emitted chunk is inside the caller supplied scratch buffer. The callback must consume the
 * bytes synchronously. No chunk that would cross [limit] is emitted, and the scratch buffer is
 * wiped after every chunk and on every exit path.
 */
@Throws(IOException::class)
internal fun streamBoundedChunks(
    input: InputStream,
    limit: Long,
    scratch: ByteArray,
    emit: (ByteArray, Int) -> Unit,
): Long {
    require(limit >= 0)
    require(scratch.isNotEmpty())
    var total = 0L
    var emptyReads = 0
    try {
        while (true) {
            val count = input.read(scratch)
            if (count < 0) return total
            if (count == 0) {
                if (++emptyReads > MAX_EMPTY_READS) throw IOException("Input stream stalled")
                continue
            }
            emptyReads = 0
            if (count.toLong() > limit - total) {
                throw IOException("File size exceeds the $limit-byte limit")
            }
            emit(scratch, count)
            scratch.fill(0, 0, count)
            total += count
        }
    } finally {
        scratch.fill(0)
    }
}

private const val MAX_EMPTY_READS = 16
