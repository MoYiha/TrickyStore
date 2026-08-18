package cleveres.tricky.cleverestech.util

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Bounded streaming primitive shared by secure-file regression tests.
 *
 * The production broker path uses the same declared-length rules: exact byte count, bounded
 * zero-read tolerance, no trailing byte, and scratch zeroization on every exit path.
 */
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

private const val MAX_EMPTY_READS = 16
