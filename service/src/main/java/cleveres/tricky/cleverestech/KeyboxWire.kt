package cleveres.tricky.cleverestech

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Strict decoder for the bounded Rust keybox response. Private key bytes stay mutable. */
internal object KeyboxWire {
    data class RawKey(
        val algorithm: String,
        val privateKeyPkcs8: ByteArray,
        val certificatesPem: List<String>,
    ) {
        fun wipePrivateKey() {
            privateKeyPkcs8.fill(0)
        }
    }

    data class Document(
        val declaredKeyboxes: Int,
        val keyboxCount: Int,
        val keys: List<RawKey>,
    ) {
        fun wipePrivateKeys() {
            keys.forEach(RawKey::wipePrivateKey)
        }
    }

    fun decode(response: ByteArray): Document? {
        if (response.size !in MIN_RESPONSE_BYTES..MAX_RESPONSE_BYTES) {
            response.fill(0)
            return null
        }
        val decodedKeys = ArrayList<RawKey>()
        return try {
            val cursor = Cursor(response)
            requireWire(cursor.readU8() == WIRE_VERSION)
            val declaredKeyboxes = cursor.readU8()
            val keyboxCount = cursor.readU8()
            val keyCount = cursor.readU16()
            requireWire(declaredKeyboxes in 1..MAX_KEYBOXES_PER_FILE)
            requireWire(keyboxCount == declaredKeyboxes)
            requireWire(keyCount in keyboxCount..keyboxCount * MAX_KEYS_PER_KEYBOX)

            repeat(keyCount) {
                val algorithmLength = cursor.readU8()
                val certificateCount = cursor.readU8()
                val privateKeyLength = cursor.readU32AsInt()
                requireWire(algorithmLength > 0)
                requireWire(certificateCount in 1..MAX_CERTIFICATES_PER_CHAIN)
                requireWire(privateKeyLength in 1..MAX_PRIVATE_KEY_DER_BYTES)

                val algorithm = cursor.readUtf8(algorithmLength)
                requireWire(
                    algorithm.equals("EC", ignoreCase = true) ||
                        algorithm.equals("ECDSA", ignoreCase = true) ||
                        algorithm.equals("RSA", ignoreCase = true),
                )
                val privateKey = cursor.readBytes(privateKeyLength)
                try {
                    val certificates = ArrayList<String>(certificateCount)
                    repeat(certificateCount) {
                        val certificateLength = cursor.readU32AsInt()
                        requireWire(certificateLength in 1..MAX_PEM_UTF8_BYTES)
                        val certificate = cursor.readUtf8(certificateLength)
                        requireWire(certificate.length in 1..MAX_PEM_UTF16_UNITS)
                        certificates += certificate
                    }
                    decodedKeys += RawKey(algorithm, privateKey, certificates)
                } catch (error: Throwable) {
                    privateKey.fill(0)
                    throw error
                }
            }
            requireWire(cursor.isAtEnd())
            Document(declaredKeyboxes, keyboxCount, decodedKeys)
        } catch (_: WireFormatException) {
            decodedKeys.forEach(RawKey::wipePrivateKey)
            null
        } catch (_: CharacterCodingException) {
            decodedKeys.forEach(RawKey::wipePrivateKey)
            null
        } catch (_: ArithmeticException) {
            decodedKeys.forEach(RawKey::wipePrivateKey)
            null
        } catch (error: Throwable) {
            decodedKeys.forEach(RawKey::wipePrivateKey)
            throw error
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

    private const val WIRE_VERSION = 2
    private const val MIN_RESPONSE_BYTES = 5
    private const val MAX_KEYBOXES_PER_FILE = 64
    private const val MAX_KEYS_PER_KEYBOX = 4
    private const val MAX_CERTIFICATES_PER_CHAIN = 16
    private const val MAX_PEM_UTF16_UNITS = 256 * 1024
    private const val MAX_PEM_UTF8_BYTES = 3 * MAX_PEM_UTF16_UNITS
    private const val MAX_PRIVATE_KEY_DER_BYTES = MAX_PEM_UTF8_BYTES
    private const val MAX_KEYBOX_XML_BYTES = 10 * 1024 * 1024
    private const val MAX_TOTAL_KEYS = MAX_KEYBOXES_PER_FILE * MAX_KEYS_PER_KEYBOX
    private const val MAX_TOTAL_CERTIFICATES = MAX_TOTAL_KEYS * MAX_CERTIFICATES_PER_CHAIN
    private const val FIXED_HEADER_BYTES = 5
    private const val KEY_HEADER_BYTES = 6
    private const val CERTIFICATE_HEADER_BYTES = 4
    private const val MAX_WIRE_OVERHEAD_BYTES =
        FIXED_HEADER_BYTES +
            MAX_TOTAL_KEYS * KEY_HEADER_BYTES +
            MAX_TOTAL_CERTIFICATES * CERTIFICATE_HEADER_BYTES
    private const val MAX_RESPONSE_BYTES = MAX_KEYBOX_XML_BYTES + MAX_WIRE_OVERHEAD_BYTES
}
