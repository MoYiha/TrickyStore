package cleveres.tricky.cleverestech

import androidx.annotation.VisibleForTesting
import java.io.OutputStream
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

    data class UnlockPayload(
        val recoveryKey: ByteArray,
        val payload: Payload,
    ) {
        fun wipeRecoveryKey() = recoveryKey.fill(0)
    }

    @Volatile
    @VisibleForTesting
    internal var openOverride: ((ByteArray, String, String?) -> Payload?)? = null

    fun open(
        encrypted: ByteArray,
        password: String,
        publicKey: String?,
    ): Payload? {
        val override = openOverride
        return if (override != null) {
            override(encrypted, password, publicKey)
        } else {
            passwordRequest(OP_CRYPTO_CBOX_OPEN, encrypted, password, publicKey, expectRecoveryKey = false)?.payload
        }
    }

    fun unlockForRecovery(
        encrypted: ByteArray,
        password: String,
        publicKey: String?,
    ): UnlockPayload? = passwordRequest(OP_CBOX_UNLOCK, encrypted, password, publicKey, expectRecoveryKey = true)

    fun recover(
        encrypted: ByteArray,
        recoveryKey: ByteArray,
        publicKey: String?,
    ): Payload? {
        if (encrypted.isEmpty() ||
            encrypted.size > MAX_CBOX_BYTES ||
            recoveryKey.size != RECOVERY_KEY_BYTES ||
            !isPublicKeyWithinLimit(publicKey)
        ) {
            return null
        }
        val publicKeyBytes = publicKey?.toByteArray(Charsets.UTF_8) ?: EMPTY_BYTES
        try {
            if (publicKeyBytes.size > MAX_PUBLIC_KEY_BYTES || recoveryKey.all { it == 0.toByte() }) return null
            val payloadLength = checkedAdd(2, publicKeyBytes.size, recoveryKey.size, encrypted.size) ?: return null
            val response =
                NativeBackend.transact(
                    OP_CBOX_RECOVER,
                    payloadLength,
                    MAX_CBOX_RESPONSE_BYTES,
                    propagateTransportFailure = true,
                ) { output ->
                    writeU16(output, publicKeyBytes.size)
                    if (publicKeyBytes.isNotEmpty()) output.write(publicKeyBytes)
                    output.write(recoveryKey)
                    output.write(encrypted)
                } ?: return null
            return decode(response)
        } finally {
            if (publicKeyBytes !== EMPTY_BYTES) publicKeyBytes.fill(0)
        }
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        openOverride = null
    }

    internal fun isPublicKeyWithinLimit(publicKey: String?): Boolean {
        if (publicKey == null) return true
        // Every UTF-16 code unit produces at least one UTF-8 byte. Reject oversized strings
        // before encoding so callers cannot turn validation into an unbounded allocation.
        if (publicKey.length > MAX_PUBLIC_KEY_BYTES) return false
        return publicKey.toByteArray(Charsets.UTF_8).size <= MAX_PUBLIC_KEY_BYTES
    }

    private fun passwordRequest(
        opcode: Int,
        encrypted: ByteArray,
        password: String,
        publicKey: String?,
        expectRecoveryKey: Boolean,
    ): UnlockPayload? {
        if (encrypted.isEmpty() || encrypted.size > MAX_CBOX_BYTES || !isPublicKeyWithinLimit(publicKey)) return null
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val publicKeyBytes = publicKey?.toByteArray(Charsets.UTF_8) ?: EMPTY_BYTES
        try {
            if (passwordBytes.size > MAX_PASSWORD_BYTES || publicKeyBytes.size > MAX_PUBLIC_KEY_BYTES) return null
            val payloadLength = checkedAdd(2, passwordBytes.size, 2, publicKeyBytes.size, encrypted.size) ?: return null
            val responseLimit = if (expectRecoveryKey) MAX_CBOX_UNLOCK_RESPONSE_BYTES else MAX_CBOX_RESPONSE_BYTES
            val response =
                NativeBackend.transact(
                    opcode,
                    payloadLength,
                    responseLimit,
                    propagateTransportFailure = true,
                ) { output ->
                    writeU16(output, passwordBytes.size)
                    if (passwordBytes.isNotEmpty()) output.write(passwordBytes)
                    writeU16(output, publicKeyBytes.size)
                    if (publicKeyBytes.isNotEmpty()) output.write(publicKeyBytes)
                    output.write(encrypted)
                } ?: return null
            return if (expectRecoveryKey) decodeUnlock(response) else {
                val payload = decode(response) ?: return null
                UnlockPayload(EMPTY_BYTES, payload)
            }
        } finally {
            passwordBytes.fill(0)
            if (publicKeyBytes !== EMPTY_BYTES) publicKeyBytes.fill(0)
        }
    }

    private fun decodeUnlock(bytes: ByteArray): UnlockPayload? {
        if (bytes.size <= RECOVERY_KEY_BYTES) {
            bytes.fill(0)
            return null
        }
        var recoveryKey: ByteArray? = null
        var metadata: ByteArray? = null
        return try {
            recoveryKey = bytes.copyOfRange(0, RECOVERY_KEY_BYTES)
            if (recoveryKey.all { it == 0.toByte() }) return null
            metadata = bytes.copyOfRange(RECOVERY_KEY_BYTES, bytes.size)
            val payload = decode(metadata) ?: return null
            metadata = null
            UnlockPayload(recoveryKey, payload).also { recoveryKey = null }
        } finally {
            recoveryKey?.fill(0)
            metadata?.fill(0)
            bytes.fill(0)
        }
    }

    @VisibleForTesting
    internal fun decode(bytes: ByteArray): Payload? {
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
            if (wireEnd != bytes.size || authorLength > MAX_AUTHOR_BYTES || wireLength > MAX_KEYBOX_WIRE_BYTES) return null
            val author = decodeUtf8Strict(bytes, authorStart, authorLength)
            if (author.length > MAX_AUTHOR_UTF16_UNITS) return null
            keyboxWire = bytes.copyOfRange(authorEnd, wireEnd)
            val document = KeyboxWire.decode(keyboxWire) ?: return null
            keyboxWire = null
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

    private fun writeU16(output: OutputStream, value: Int) {
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
    private const val OP_CBOX_UNLOCK = 29
    private const val OP_CBOX_RECOVER = 31
    private const val RESPONSE_PREFIX_BYTES = 7
    private const val RECOVERY_KEY_BYTES = 32
    private const val MAX_PASSWORD_BYTES = 4 * 1024
    private const val MAX_PUBLIC_KEY_BYTES = 16 * 1024
    private const val MAX_AUTHOR_UTF16_UNITS = 1024
    private const val MAX_AUTHOR_BYTES = 4 * MAX_AUTHOR_UTF16_UNITS
    private const val MAX_CBOX_BYTES = CboxWireLimits.MAX_BYTES
    private const val MAX_KEYBOX_WIRE_BYTES = KeyboxWire.MAX_RESPONSE_BYTES
    private const val MAX_CBOX_RESPONSE_BYTES = RESPONSE_PREFIX_BYTES + MAX_AUTHOR_BYTES + MAX_KEYBOX_WIRE_BYTES
    private const val MAX_CBOX_UNLOCK_RESPONSE_BYTES = RECOVERY_KEY_BYTES + MAX_CBOX_RESPONSE_BYTES
    private val EMPTY_BYTES = ByteArray(0)
}
