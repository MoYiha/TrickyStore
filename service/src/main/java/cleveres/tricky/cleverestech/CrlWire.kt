package cleveres.tricky.cleverestech

import java.io.OutputStream

/** Thin bounded wire adapter for CRL work executed by the unprivileged Rust backend. */
internal object CrlWire {
    data class Query(
        val serialTwosComplement: ByteArray,
        val subjectPublicKeyInfo: ByteArray,
    )

    data class Result(
        val rawEntryCount: Int,
        val normalizedEntryCount: Int,
        val revoked: BooleanArray,
    )

    fun requestLength(
        crlBytes: Int,
        queries: List<Query>,
    ): Int? {
        if (crlBytes !in 1..MAX_CRL_BYTES || queries.size > MAX_QUERY_COUNT) return null
        var total = REQUEST_PREFIX_BYTES
        return try {
            total = Math.addExact(total, crlBytes)
            for (query in queries) {
                if (query.serialTwosComplement.size !in 1..MAX_SERIAL_BYTES ||
                    query.subjectPublicKeyInfo.size !in 1..MAX_SPKI_BYTES
                ) {
                    return null
                }
                total = Math.addExact(total, QUERY_PREFIX_BYTES)
                total = Math.addExact(total, query.serialTwosComplement.size)
                total = Math.addExact(total, query.subjectPublicKeyInfo.size)
                if (total > MAX_REQUEST_BYTES) return null
            }
            total.takeIf { it <= MAX_REQUEST_BYTES }
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun writeRequest(
        output: OutputStream,
        crl: ByteArray,
        queries: List<Query>,
    ) {
        require(requestLength(crl.size, queries) != null) { "CRL request exceeds configured bound" }
        writeU32(output, crl.size)
        writeU16(output, queries.size)
        output.write(crl)
        for (query in queries) {
            writeU16(output, query.serialTwosComplement.size)
            writeU32(output, query.subjectPublicKeyInfo.size)
            output.write(query.serialTwosComplement)
            output.write(query.subjectPublicKeyInfo)
        }
    }

    fun decode(
        response: ByteArray,
        expectedQueries: Int,
    ): Result? {
        if (expectedQueries !in 0..MAX_QUERY_COUNT) {
            response.fill(0)
            return null
        }
        val expectedLength = RESPONSE_PREFIX_BYTES + ((expectedQueries + 7) / 8)
        if (response.size != expectedLength || response.size > MAX_RESPONSE_BYTES) {
            response.fill(0)
            return null
        }
        return try {
            if ((response[0].toInt() and 0xff) != WIRE_VERSION) return null
            val rawCount = readU32(response, 1)
            val normalizedCount = readU32(response, 5)
            val queryCount = readU16(response, 9)
            if (rawCount !in 0..MAX_CRL_ENTRIES ||
                normalizedCount !in 0..MAX_NORMALIZED_ENTRIES ||
                queryCount != expectedQueries
            ) {
                return null
            }
            if (expectedQueries % 8 != 0 && expectedQueries != 0) {
                val usedBits = expectedQueries % 8
                val invalidMask = (0xff shl usedBits) and 0xff
                if ((response.last().toInt() and invalidMask) != 0) return null
            }
            val revoked = BooleanArray(expectedQueries)
            for (index in 0 until expectedQueries) {
                val byte = response[RESPONSE_PREFIX_BYTES + index / 8].toInt() and 0xff
                revoked[index] = byte and (1 shl (index % 8)) != 0
            }
            Result(rawCount, normalizedCount, revoked)
        } finally {
            response.fill(0)
        }
    }

    private fun writeU16(
        output: OutputStream,
        value: Int,
    ) {
        require(value in 0..0xffff)
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private fun writeU32(
        output: OutputStream,
        value: Int,
    ) {
        require(value >= 0)
        output.write((value ushr 24) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private fun readU16(
        input: ByteArray,
        offset: Int,
    ): Int = ((input[offset].toInt() and 0xff) shl 8) or (input[offset + 1].toInt() and 0xff)

    private fun readU32(
        input: ByteArray,
        offset: Int,
    ): Int =
        ((input[offset].toInt() and 0xff) shl 24) or
            ((input[offset + 1].toInt() and 0xff) shl 16) or
            ((input[offset + 2].toInt() and 0xff) shl 8) or
            (input[offset + 3].toInt() and 0xff)

    const val MAX_CRL_BYTES = 8 * 1024 * 1024
    const val MAX_QUERY_COUNT = 16 * 1024
    const val MAX_SERIAL_BYTES = 256
    const val MAX_SPKI_BYTES = 64 * 1024
    const val MAX_REQUEST_BYTES = 24 * 1024 * 1024
    const val MAX_CRL_ENTRIES = 1_000_000
    const val MAX_NORMALIZED_ENTRIES = 1_000_000
    private const val WIRE_VERSION = 1
    private const val REQUEST_PREFIX_BYTES = 6
    private const val QUERY_PREFIX_BYTES = 6
    private const val RESPONSE_PREFIX_BYTES = 11
    const val MAX_RESPONSE_BYTES = RESPONSE_PREFIX_BYTES + MAX_QUERY_COUNT / 8
}
