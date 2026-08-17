package cleveres.tricky.cleverestech

import java.io.OutputStream

/** Thin bounded wire adapter for immutable CRL snapshots owned by the Rust backend. */
object CrlWire {
    /**
     * Opaque Rust CRL generation. The empty [Set] surface is retained only for source compatibility
     * with managed test seams that historically injected normalized revocation sets. Production
     * callers are detected by identity/type and never enumerate or populate a managed CRL set.
     */
    data class Handle(
        val generation: Long,
        val rawEntryCount: Int,
        val normalizedEntryCount: Int,
    ) : Set<String> {
        override val size: Int get() = 0

        override fun contains(element: String): Boolean = false

        override fun containsAll(elements: Collection<String>): Boolean = elements.isEmpty()

        override fun isEmpty(): Boolean = true

        override fun iterator(): Iterator<String> = emptySet<String>().iterator()
    }

    data class Query(
        val serialTwosComplement: ByteArray,
        val subjectPublicKeyInfo: ByteArray,
    )

    data class Result(
        val generation: Long,
        val revoked: BooleanArray,
    )

    fun refreshLength(crlBytes: Int): Int? =
        if (crlBytes in 1..MAX_CRL_BYTES) REFRESH_PREFIX_BYTES + crlBytes else null

    fun writeRefresh(
        output: OutputStream,
        crl: ByteArray,
    ) {
        require(refreshLength(crl.size) != null)
        output.write(WIRE_VERSION)
        output.write(ACTION_REFRESH)
        writeU32(output, crl.size)
        output.write(crl)
    }

    fun decodeRefresh(response: ByteArray): Handle? {
        if (response.size != REFRESH_RESPONSE_BYTES) {
            response.fill(0)
            return null
        }
        return try {
            if ((response[0].toInt() and 0xff) != WIRE_VERSION ||
                (response[1].toInt() and 0xff) != ACTION_REFRESH
            ) {
                return null
            }
            val generation = readU64(response, 2)
            val raw = readU32(response, 10)
            val normalized = readU32(response, 14)
            if (generation <= 0 || raw !in 0..MAX_CRL_ENTRIES || normalized !in 0..MAX_NORMALIZED_ENTRIES) {
                return null
            }
            Handle(generation, raw, normalized)
        } finally {
            response.fill(0)
        }
    }

    fun queryLength(queries: List<Query>): Int? {
        if (queries.size > MAX_QUERY_COUNT) return null
        var total = QUERY_PREFIX_BYTES
        return try {
            for (query in queries) {
                if (query.serialTwosComplement.size !in 1..MAX_SERIAL_BYTES ||
                    query.subjectPublicKeyInfo.size !in 1..MAX_SPKI_BYTES
                ) {
                    return null
                }
                total = Math.addExact(total, QUERY_ENTRY_PREFIX_BYTES)
                total = Math.addExact(total, query.serialTwosComplement.size)
                total = Math.addExact(total, query.subjectPublicKeyInfo.size)
                if (total > MAX_REQUEST_BYTES) return null
            }
            total
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun writeQuery(
        output: OutputStream,
        generation: Long,
        queries: List<Query>,
    ) {
        require(generation > 0 && queryLength(queries) != null)
        output.write(WIRE_VERSION)
        output.write(ACTION_QUERY)
        writeU64(output, generation)
        writeU16(output, queries.size)
        for (query in queries) {
            writeU16(output, query.serialTwosComplement.size)
            writeU32(output, query.subjectPublicKeyInfo.size)
            output.write(query.serialTwosComplement)
            output.write(query.subjectPublicKeyInfo)
        }
    }

    fun decodeQuery(
        response: ByteArray,
        expectedGeneration: Long,
        expectedQueries: Int,
    ): Result? {
        if (expectedGeneration <= 0 || expectedQueries !in 0..MAX_QUERY_COUNT) {
            response.fill(0)
            return null
        }
        val expectedLength = QUERY_RESPONSE_PREFIX_BYTES + ((expectedQueries + 7) / 8)
        if (response.size != expectedLength || response.size > MAX_RESPONSE_BYTES) {
            response.fill(0)
            return null
        }
        return try {
            if ((response[0].toInt() and 0xff) != WIRE_VERSION ||
                (response[1].toInt() and 0xff) != ACTION_QUERY ||
                readU64(response, 2) != expectedGeneration ||
                readU16(response, 10) != expectedQueries
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
                val byte = response[QUERY_RESPONSE_PREFIX_BYTES + index / 8].toInt() and 0xff
                revoked[index] = byte and (1 shl (index % 8)) != 0
            }
            Result(expectedGeneration, revoked)
        } finally {
            response.fill(0)
        }
    }

    private fun writeU16(output: OutputStream, value: Int) {
        require(value in 0..0xffff)
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private fun writeU32(output: OutputStream, value: Int) {
        require(value >= 0)
        output.write((value ushr 24) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private fun writeU64(output: OutputStream, value: Long) {
        require(value > 0)
        for (shift in 56 downTo 0 step 8) output.write((value ushr shift).toInt() and 0xff)
    }

    private fun readU16(input: ByteArray, offset: Int): Int =
        ((input[offset].toInt() and 0xff) shl 8) or (input[offset + 1].toInt() and 0xff)

    private fun readU32(input: ByteArray, offset: Int): Int =
        ((input[offset].toInt() and 0xff) shl 24) or
            ((input[offset + 1].toInt() and 0xff) shl 16) or
            ((input[offset + 2].toInt() and 0xff) shl 8) or
            (input[offset + 3].toInt() and 0xff)

    private fun readU64(input: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) value = (value shl 8) or (input[offset + index].toLong() and 0xffL)
        return value
    }

    const val MAX_CRL_BYTES = 8 * 1024 * 1024
    const val MAX_QUERY_COUNT = 16 * 1024
    const val MAX_SERIAL_BYTES = 256
    const val MAX_SPKI_BYTES = 64 * 1024
    const val MAX_REQUEST_BYTES = 24 * 1024 * 1024
    const val MAX_CRL_ENTRIES = 1_000_000
    const val MAX_NORMALIZED_ENTRIES = 1_000_000
    private const val WIRE_VERSION = 2
    private const val ACTION_REFRESH = 1
    private const val ACTION_QUERY = 2
    private const val REFRESH_PREFIX_BYTES = 6
    private const val REFRESH_RESPONSE_BYTES = 18
    private const val QUERY_PREFIX_BYTES = 12
    private const val QUERY_ENTRY_PREFIX_BYTES = 6
    private const val QUERY_RESPONSE_PREFIX_BYTES = 12
    const val MAX_RESPONSE_BYTES = QUERY_RESPONSE_PREFIX_BYTES + MAX_QUERY_COUNT / 8
}
