package cleveres.tricky.cleverestech

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Thin transport adapter for Rust CBOX decrypt -> verify -> keybox registration. */
internal object FusedCboxBackend {
    data class Payload(
        val author: String,
        val document: KeyboxWire.Document,
        val hasSignature: Boolean,
    )

    fun open(
        encrypted: ByteArray,
        password: String,
        publicKey: String?,
    ): Payload? {
        if (encrypted.isEmpty() || encrypted.size > MAX_CBOX_BYTES) return null
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val publicKeyBytes = publicKey?.toByteArray(Charsets.UTF_8) ?: EMPTY_BYTES
        try {
            if (passwordBytes.size > MAX_PASSWORD_BYTES || publicKeyBytes.size > MAX_PUBLIC_KEY_BYTES) return null
            val payloadLength = checkedAdd(2, passwordBytes.size, 2, publicKeyBytes.size, encrypted.size) ?: return null
            val response =
                NativeBackend.transact(
                    OP_CRYPTO_CBOX_OPEN,
                    payloadLength,
                    MAX_CBOX_RESPONSE_BYTES,
                    propagateTransportFailure = true,
                ) { output ->
                    writeU16(output, passwordBytes.size)
                    if (passwordBytes.isNotEmpty()) output.write(passwordBytes)
                    writeU16(output, publicKeyBytes.size)
                    if (publicKeyBytes.isNotEmpty()) output.write(publicKeyBytes)
                    output.write(encrypted)
                } ?: return null
            return decode(response)
        } finally {
            passwordBytes.fill(0)
            if (publicKeyBytes !== EMPTY_BYTES) publicKeyBytes.fill(0)
        }
    }

    private fun decode(bytes: ByteArray): Payload? {
        if (bytes.size < RESPONSE_PREFIX_BYTES) {
            bytes.fill(0)
            return null
        }
        var keyboxWire: ByteArray? = null
        return try {
            val authorLength = readU16(bytes, 0)
            val wireLength = readU32(bytes, 2)
            val signatureFlag = bytes[6].toInt() and 0xff
            if (wireLength > Int.MAX_VALUE || signatureFlag !in 0..1) return null
            val authorStart = RESPONSE_PREFIX_BYTES
            val authorEnd = Math.addExact(authorStart, authorLength)
            val wireEnd = Math.addExact(authorEnd, wireLength.toInt())
            if (wireEnd != bytes.size || authorLength > MAX_AUTHOR_BYTES || wireLength > MAX_KEYBOX_WIRE_BYTES) {
                return null
            }
            val author = decodeUtf8Strict(bytes, authorStart, authorLength)
            keyboxWire = bytes.copyOfRange(authorEnd, wireEnd)
            val document = KeyboxWire.decode(keyboxWire) ?: return null
            keyboxWire = null // ownership was consumed and wiped by KeyboxWire.decode
            Payload(author, document, signatureFlag == 1)
        } catch (_: CharacterCodingException) {
            null
        } catch (_: ArithmeticException) {
            null
        } finally {
            keyboxWire?.fill(0)
            bytes.fill(0)
        }
    }

    private fun decodeUtf8Strict(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, length))
            .toString()

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL))

    private fun writeU16(output: java.io.OutputStream, value: Int) {
        require(value in 0..0xffff)
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private fun checkedAdd(vararg values: Int): Int? {
        var total = 0
        return try {
            for (value in values) {
                if (value < 0) return null
                total = Math.addExact(total, value)
            }
            total
        } catch (_: ArithmeticException) {
            null
        }
    }

    private const val OP_CRYPTO_CBOX_OPEN = 20
    private const val RESPONSE_PREFIX_BYTES = 7
    private const val MAX_PASSWORD_BYTES = 4 * 1024
    private const val MAX_PUBLIC_KEY_BYTES = 16 * 1024
    private const val MAX_AUTHOR_BYTES = 1024
    private const val MAX_CBOX_BYTES = 10 * 1024 * 1024 + 36
    private const val MAX_KEYBOX_WIRE_BYTES = 10 * 1024 * 1024 + 64 * 1024
    private const val MAX_CBOX_RESPONSE_BYTES = RESPONSE_PREFIX_BYTES + MAX_AUTHOR_BYTES + MAX_KEYBOX_WIRE_BYTES
    private val EMPTY_BYTES = ByteArray(0)
}
