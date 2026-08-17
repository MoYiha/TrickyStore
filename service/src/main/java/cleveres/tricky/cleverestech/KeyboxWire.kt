package cleveres.tricky.cleverestech

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Strict decoder for public keybox metadata. Private key material never crosses this wire. */
internal object KeyboxWire {
    data class RawKey(
        val algorithm: String,
        val keyId: ByteArray,
        val certificatesDer: List<ByteArray>,
    )

    data class Document(
        val declaredKeyboxes: Int,
        val keyboxCount: Int,
        val keys: List<RawKey>,
    )

    fun decode(response: ByteArray): Document? {
        if (response.size !in MIN_RESPONSE_BYTES..MAX_RESPONSE_BYTES) {
            response.fill(0)
            return null
        }
        return try {
            val cursor = Cursor(response)
            requireWire(cursor.readU8() == WIRE_VERSION)
            val declaredKeyboxes = cursor.readU8()
            val keyboxCount = cursor.readU8()
            val keyCount = cursor.readU16()
            requireWire(declaredKeyboxes in 1..MAX_KEYBOXES_PER_FILE)
            requireWire(keyboxCount == declaredKeyboxes)
            requireWire(keyCount in keyboxCount..keyboxCount * MAX_KEYS_PER_KEYBOX)

            val decodedKeys = ArrayList<RawKey>(keyCount)
            repeat(keyCount) {
                val algorithmLength = cursor.readU8()
                val certificateCount = cursor.readU8()
                requireWire(algorithmLength > 0)
                requireWire(certificateCount in 1..MAX_CERTIFICATES_PER_CHAIN)

                val keyId = cursor.readBytes(KEY_ID_BYTES)
                requireWire(keyId.any { byte -> byte != 0.toByte() })
                val algorithm = cursor.readUtf8(algorithmLength)
                requireWire(
                    algorithm.equals("EC", ignoreCase = true) ||
                        algorithm.equals("RSA", ignoreCase = true),
                )
                val certificates = ArrayList<ByteArray>(certificateCount)
                repeat(certificateCount) {
                    val certificateLength = cursor.readU32AsInt()
                    requireWire(certificateLength in 1..MAX_CERTIFICATE_DER_BYTES)
                    certificates += cursor.readBytes(certificateLength)
                }
                decodedKeys += RawKey(algorithm, keyId, certificates)
            }
            requireWire(cursor.isAtEnd())
            Document(declaredKeyboxes, keyboxCount, decodedKeys)
        } catch (_: WireFormatException) {
            null
        } catch (_: CharacterCodingException) {
            null
        } catch (_: ArithmeticException) {
            null
        } finally {
            response.fill(0)
        }
    }

    private class Cursor(
        private val bytes: ByteArray,
    ) {
        private var offset = 0

        fun readU8(): Int {
            requireAvailable(1)
            return bytes[offset++].toInt() and 0xff
        }

        fun readU16(): Int {
            requireAvailable(2)
            val value =
                ((bytes[offset].toInt() and 0xff) shl 8) or
                    (bytes[offset + 1].toInt() and 0xff)
            offset += 2
            return value
        }

        fun readU32AsInt(): Int {
            requireAvailable(4)
            val value =
                ((bytes[offset].toLong() and 0xffL) shl 24) or
                    ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
                    ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
                    (bytes[offset + 3].toLong() and 0xffL)
            offset += 4
            requireWire(value <= Int.MAX_VALUE.toLong())
            return value.toInt()
        }

        fun readBytes(length: Int): ByteArray {
            requireWire(length >= 0)
            requireAvailable(length)
            val output = bytes.copyOfRange(offset, offset + length)
            offset += length
            return output
        }

        fun readUtf8(length: Int): String {
            requireWire(length >= 0)
            requireAvailable(length)
            val value =
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString()
            offset += length
            return value
        }

        fun isAtEnd(): Boolean = offset == bytes.size

        private fun requireAvailable(length: Int) {
            val end = Math.addExact(offset, length)
            requireWire(end <= bytes.size)
        }
    }

    private class WireFormatException : Exception()

    private fun requireWire(condition: Boolean) {
        if (!condition) throw WireFormatException()
    }

    private const val WIRE_VERSION = 3
    private const val KEY_ID_BYTES = 16
    private const val MIN_RESPONSE_BYTES = 5
    private const val MAX_KEYBOXES_PER_FILE = 64
    private const val MAX_KEYS_PER_KEYBOX = 4
    private const val MAX_CERTIFICATES_PER_CHAIN = 16
    private const val MAX_CERTIFICATE_DER_BYTES = 256 * 1024
    private const val MAX_KEYBOX_XML_BYTES = 10 * 1024 * 1024
    private const val MAX_TOTAL_KEYS = MAX_KEYBOXES_PER_FILE * MAX_KEYS_PER_KEYBOX
    private const val MAX_TOTAL_CERTIFICATES = MAX_TOTAL_KEYS * MAX_CERTIFICATES_PER_CHAIN
    private const val FIXED_HEADER_BYTES = 5
    private const val KEY_HEADER_BYTES = 2 + KEY_ID_BYTES
    private const val CERTIFICATE_HEADER_BYTES = 4
    private const val MAX_WIRE_OVERHEAD_BYTES =
        FIXED_HEADER_BYTES +
            MAX_TOTAL_KEYS * KEY_HEADER_BYTES +
            MAX_TOTAL_CERTIFICATES * CERTIFICATE_HEADER_BYTES
    private const val MAX_RESPONSE_BYTES = MAX_KEYBOX_XML_BYTES + MAX_WIRE_OVERHEAD_BYTES
}
