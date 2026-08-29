package cleveres.tricky.cleverestech

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal enum class BackendStatus(val wireValue: Int) {
    REJECTED(1),
    UNKNOWN_KEY_ID(2),
    STALE_GENERATION(3),
    STATE_RESET(4),
    ;

    companion object {
        fun fromWire(value: Int): BackendStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

internal class RustBackendStateException(
    val status: BackendStatus,
) : IOException("Rust backend state rejected operation: $status")

/** Thin Android LocalSocket adapter for the unprivileged Rust backend. */
object NativeBackend {
    internal data class BackendIdentity(
        val pid: Int,
        val epochHigh: Long,
        val epochLow: Long,
    )

    private var socket: LocalSocket? = null
    private var backendIdentity: BackendIdentity? = null
    private var backendStateResetPending = false
    private val readHeaderBuffer = ByteArray(HEADER_BYTES)
    private val writeHeaderBuffer = ByteArray(HEADER_BYTES)

    fun encryptBackup(
        plaintext: ByteArray,
        password: String,
    ): ByteArray? = transformBackup(OP_CRYPTO_BACKUP_ENCRYPT, plaintext, password)

    fun decryptBackup(
        encrypted: ByteArray,
        password: String,
    ): ByteArray? = transformBackup(OP_CRYPTO_BACKUP_DECRYPT, encrypted, password)

    internal fun parseKeybox(xml: ByteArray): KeyboxWire.Document? {
        if (xml.isEmpty() || xml.size > KeyboxWire.MAX_XML_BYTES) {
            xml.fill(0)
            return null
        }
        return try {
            val response =
                transact(
                    OP_KEYBOX_PARSE,
                    xml.size,
                    KeyboxWire.MAX_RESPONSE_BYTES,
                    propagateTransportFailure = true,
                ) { output -> output.write(xml) }
            decodeKeyboxResponse(response)
        } finally {
            xml.fill(0)
        }
    }

    internal fun parseKeyboxFile(
        scope: Int,
        filename: String,
    ): KeyboxWire.Document? {
        if (scope !in KEYBOX_FILE_SCOPE_CONFIG_ROOT..KEYBOX_FILE_SCOPE_DIRECTORY ||
            filename.isEmpty() ||
            filename == "." ||
            filename == ".." ||
            filename.indexOf('/') >= 0 ||
            filename.indexOf('\u0000') >= 0
        ) {
            return null
        }
        val filenameBytes = filename.toByteArray(Charsets.UTF_8)
        try {
            if (filenameBytes.size > MAX_KEYBOX_FILE_NAME_BYTES) return null
            val payloadLength = checkedPayloadLength(1, filenameBytes.size) ?: return null
            val response =
                transact(
                    OP_KEYBOX_FILE_PARSE,
                    payloadLength,
                    KeyboxWire.MAX_RESPONSE_BYTES,
                    propagateTransportFailure = true,
                ) { output ->
                    output.write(scope)
                    output.write(filenameBytes)
                }
            return decodeKeyboxResponse(response)
        } finally {
            filenameBytes.fill(0)
        }
    }

    internal fun awaitReady(timeoutMs: Long): Boolean {
        require(timeoutMs in 1..MAX_STARTUP_WAIT_MS)
        val deadlineNanos = System.nanoTime() + timeoutMs * NANOS_PER_MILLISECOND
        var sleepMs = STARTUP_RETRY_INITIAL_MS
        while (true) {
            try {
                synchronized(this) { connectedSocket() }
                return true
            } catch (_: Exception) {
                synchronized(this) { closeSocket() }
            }

            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0) return false
            val remainingMs =
                ((remainingNanos + NANOS_PER_MILLISECOND - 1) / NANOS_PER_MILLISECOND)
                    .coerceAtLeast(1)
            try {
                Thread.sleep(minOf(sleepMs, remainingMs))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
            sleepMs = minOf(sleepMs * 2, STARTUP_RETRY_MAX_MS)
        }
    }

    /**
     * Recovery owner calls this exactly once after acquiring the single-flight recovery lock.
     * It connects/handshakes and acknowledges the identity transition so rebuild traffic can flow.
     */
    @Synchronized
    internal fun beginBackendRecovery(): BackendIdentity {
        connectedSocket()
        backendStateResetPending = false
        return requireNotNull(backendIdentity)
    }

    @Synchronized
    internal fun currentBackendIdentity(): BackendIdentity? = backendIdentity

    @Synchronized
    internal fun isCurrentBackendIdentity(identity: BackendIdentity): Boolean = backendIdentity == identity

    @Synchronized
    internal fun consumeBackendStateReset(): Boolean {
        val changed = backendStateResetPending
        backendStateResetPending = false
        return changed
    }

    @Synchronized
    @androidx.annotation.VisibleForTesting
    internal fun observeBackendIdentityForTesting(identity: BackendIdentity) {
        observeBackendIdentity(identity)
    }

    @Synchronized
    @androidx.annotation.VisibleForTesting
    internal fun resetIdentityForTesting() {
        closeSocket()
        backendIdentity = null
        backendStateResetPending = false
    }

    @Synchronized
    fun close() {
        closeSocket()
    }

    private fun transformBackup(
        opcode: Int,
        data: ByteArray,
        password: String,
    ): ByteArray? {
        val dataLimit = if (opcode == OP_CRYPTO_BACKUP_ENCRYPT) MAX_BACKUP_PLAINTEXT_BYTES else MAX_BACKUP_WIRE_BYTES
        if (data.size > dataLimit) return null
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        try {
            if (passwordBytes.size > MAX_PASSWORD_BYTES) return null
            val payloadLength = checkedPayloadLength(2, passwordBytes.size, data.size) ?: return null
            return transact(
                opcode,
                payloadLength,
                MAX_BACKUP_RESPONSE_BYTES,
                propagateTransportFailure = true,
            ) { output ->
                writeU16(output, passwordBytes.size)
                if (passwordBytes.isNotEmpty()) output.write(passwordBytes)
                if (data.isNotEmpty()) output.write(data)
            }
        } finally {
            passwordBytes.fill(0)
        }
    }

    internal fun transact(
        opcode: Int,
        payloadLength: Int,
        responseLimit: Int,
        propagateTransportFailure: Boolean = false,
        writePayload: (OutputStream) -> Unit,
    ): ByteArray? {
        var automaticRetryUsed = false
        while (true) {
            var identityBeforeAttempt: BackendIdentity? = null
            try {
                return synchronized(this) {
                    identityBeforeAttempt = backendIdentity
                    transactOnce(opcode, payloadLength, responseLimit, writePayload)
                }
            } catch (error: RustBackendStateException) {
                if (!automaticRetryUsed &&
                    error.status != BackendStatus.REJECTED &&
                    !BackendStateRecovery.isRecovering()
                ) {
                    val current = synchronized(this) { backendIdentity }
                    if (current != null && recoverBackendOutsideIoLock(current)) {
                        automaticRetryUsed = true
                        continue
                    }
                }
                throw error
            } catch (error: Exception) {
                val changedIdentity =
                    synchronized(this) {
                        closeSocket()
                        if (!automaticRetryUsed &&
                            !BackendStateRecovery.isRecovering() &&
                            identityBeforeAttempt != null
                        ) {
                            var candidate: BackendIdentity? = null
                            for (attempt in 0..2) {
                                candidate = runCatching {
                                    connectedSocket()
                                    backendIdentity?.takeIf { it != identityBeforeAttempt }
                                }.getOrNull()
                                if (candidate != null || attempt == 2) break
                                try {
                                    Thread.sleep(50)
                                } catch (_: InterruptedException) {
                                    Thread.currentThread().interrupt()
                                    break
                                }
                            }
                            candidate
                        } else {
                            null
                        }
                    }
                if (changedIdentity != null && recoverBackendOutsideIoLock(changedIdentity)) {
                    automaticRetryUsed = true
                    continue
                }
                synchronized(this) { closeSocket() }
                Logger.e("Rust backend operation $opcode failed: ${error.javaClass.simpleName}")
                if (propagateTransportFailure) throw RustBackendUnavailableException(error)
                return null
            }
        }
    }

    private fun recoverBackendOutsideIoLock(identity: BackendIdentity): Boolean {
        check(!Thread.holdsLock(this)) { "Backend recovery must not run while the backend IPC lock is held" }
        return BackendStateRecovery.recover(identity)
    }

    private fun transactOnce(
        opcode: Int,
        payloadLength: Int,
        responseLimit: Int,
        writePayload: (OutputStream) -> Unit,
    ): ByteArray? {
        val active = connectedSocket()
        if (backendStateResetPending && opcode != OP_BACKEND_PING) {
            throw RustBackendStateException(BackendStatus.STATE_RESET)
        }
        val output = active.outputStream
        val input = active.inputStream
        writeHeader(output, opcode, payloadLength)
        writePayload(output)
        output.flush()

        val header = readHeader(input, opcode, responseLimit)
        val response = ByteArray(header.payloadLength)
        try {
            readFully(input, response)
            if (header.flags == FLAG_ERROR) {
                val status =
                    if (response.size == BACKEND_STATUS_BYTES) {
                        BackendStatus.fromWire(response[0].toInt() and 0xff)
                    } else {
                        null
                    }
                response.fill(0)
                // Human-readable legacy error payloads are intentionally opaque to managed code.
                // Only a fixed one-byte typed status can influence recovery behavior.
                if (status == null || status == BackendStatus.REJECTED) return null
                throw RustBackendStateException(status)
            }
            return response
        } catch (error: Throwable) {
            response.fill(0)
            throw error
        }
    }

    private fun decodeKeyboxResponse(response: ByteArray?): KeyboxWire.Document? {
        if (response == null) return null
        return try {
            KeyboxWire.decode(response)
                ?: throw RustBackendUnavailableException(IOException("Invalid keybox backend response"))
        } catch (error: RustBackendUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw RustBackendUnavailableException(error)
        }
    }

    private fun connectedSocket(): LocalSocket {
        socket?.let { return it }
        val connected = LocalSocket()
        try {
            connected.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            val peer = connected.peerCredentials
            if (peer.uid != ANDROID_AID_NOBODY || peer.gid != ANDROID_GID_NOBODY || peer.pid <= 1) {
                throw IOException("Unexpected Rust backend peer credentials")
            }
            connected.setSoTimeout(IO_TIMEOUT_MS)
            val identity = readBackendIdentity(connected, peer.pid)
            observeBackendIdentity(identity)
        } catch (error: Throwable) {
            runCatching { connected.close() }
            throw error
        }
        socket = connected
        return connected
    }

    private fun readBackendIdentity(
        connected: LocalSocket,
        pid: Int,
    ): BackendIdentity {
        val output = connected.outputStream
        val input = connected.inputStream
        val capability = BackendAuth.fromEnvironment() ?: throw IOException("Backend capability unavailable")
        try {
            writeHeader(output, OP_BACKEND_PING, BACKEND_PING_REQUEST_BYTES)
            output.write(BACKEND_HANDSHAKE_VERSION)
            output.write(capability)
            output.flush()
        } finally {
            capability.fill(0)
        }
        val header = readHeader(input, OP_BACKEND_PING, BACKEND_PING_RESPONSE_BYTES)
        if (header.flags != 0 || header.payloadLength != BACKEND_PING_RESPONSE_BYTES) {
            throw IOException("Invalid backend identity frame")
        }
        val response = ByteArray(BACKEND_PING_RESPONSE_BYTES)
        try {
            readFully(input, response)
            if ((response[0].toInt() and 0xff) != BACKEND_HANDSHAKE_VERSION ||
                readU16(response, 1) != IPC_VERSION
            ) {
                throw IOException("Unsupported backend identity handshake")
            }
            val high = readI64(response, 3)
            val low = readI64(response, 11)
            if (high == 0L && low == 0L) throw IOException("Invalid zero backend epoch")
            return BackendIdentity(pid, high, low)
        } finally {
            response.fill(0)
        }
    }

    private fun observeBackendIdentity(identity: BackendIdentity) {
        val previous = backendIdentity
        if (previous != null && previous != identity) {
            backendStateResetPending = true
        }
        backendIdentity = identity
    }

    private fun closeSocket() {
        val active = socket
        socket = null
        runCatching { active?.close() }
    }

    private data class FrameHeader(
        val flags: Int,
        val payloadLength: Int,
    )

    private fun readHeader(
        input: InputStream,
        expectedOpcode: Int,
        responseLimit: Int,
    ): FrameHeader {
        readFully(input, readHeaderBuffer)
        for (index in IPC_MAGIC.indices) {
            if (readHeaderBuffer[index] != IPC_MAGIC[index]) throw IOException("Invalid backend IPC magic")
        }
        if (readU16(readHeaderBuffer, 4) != IPC_VERSION) throw IOException("Unsupported backend IPC version")
        if (readU16(readHeaderBuffer, 6) != expectedOpcode) throw IOException("Unexpected backend IPC opcode")
        val flags = readI32(readHeaderBuffer, 8)
        if (flags != 0 && flags != FLAG_ERROR) throw IOException("Unsupported backend IPC flags")
        val payloadLength = readU32(readHeaderBuffer, 12)
        val limit = if (flags == FLAG_ERROR) MAX_BACKEND_ERROR_BYTES else responseLimit
        if (payloadLength > limit.toLong()) throw IOException("Backend response exceeds operation bound")
        return FrameHeader(flags, payloadLength.toInt())
    }

    private fun writeHeader(
        output: OutputStream,
        opcode: Int,
        payloadLength: Int,
    ) {
        require(opcode in 1..0xffff && payloadLength in 1..MAX_BACKEND_REQUEST_BYTES)
        writeHeaderBuffer.fill(0)
        IPC_MAGIC.copyInto(writeHeaderBuffer, 0)
        writeU16(writeHeaderBuffer, 4, IPC_VERSION)
        writeU16(writeHeaderBuffer, 6, opcode)
        writeI32(writeHeaderBuffer, 8, 0)
        writeI32(writeHeaderBuffer, 12, payloadLength)
        output.write(writeHeaderBuffer)
    }

    private fun readFully(
        input: InputStream,
        output: ByteArray,
    ) {
        var offset = 0
        var emptyReads = 0
        while (offset < output.size) {
            val count = input.read(output, offset, output.size - offset)
            if (count < 0) throw IOException("Backend IPC frame ended early")
            if (count == 0) {
                if (++emptyReads > MAX_EMPTY_READS) throw IOException("Backend IPC stream stalled")
                continue
            }
            emptyReads = 0
            offset += count
        }
    }

    private fun checkedPayloadLength(vararg components: Int): Int? {
        var total = 0
        return try {
            for (component in components) {
                require(component >= 0)
                total = Math.addExact(total, component)
            }
            total.takeIf { it in 1..MAX_BACKEND_REQUEST_BYTES }
        } catch (_: ArithmeticException) {
            null
        } catch (_: IllegalArgumentException) {
            null
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

    private fun readU16(
        buffer: ByteArray,
        offset: Int,
    ): Int = ((buffer[offset].toInt() and 0xff) shl 8) or (buffer[offset + 1].toInt() and 0xff)

    private fun readI32(
        buffer: ByteArray,
        offset: Int,
    ): Int =
        ((buffer[offset].toInt() and 0xff) shl 24) or
            ((buffer[offset + 1].toInt() and 0xff) shl 16) or
            ((buffer[offset + 2].toInt() and 0xff) shl 8) or
            (buffer[offset + 3].toInt() and 0xff)

    private fun readI64(
        buffer: ByteArray,
        offset: Int,
    ): Long {
        var value = 0L
        for (index in 0 until 8) value = (value shl 8) or (buffer[offset + index].toLong() and 0xffL)
        return value
    }

    private fun readU32(
        buffer: ByteArray,
        offset: Int,
    ): Long =
        ((buffer[offset].toLong() and 0xffL) shl 24) or
            ((buffer[offset + 1].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 8) or
            (buffer[offset + 3].toLong() and 0xffL)

    private fun writeU16(
        buffer: ByteArray,
        offset: Int,
        value: Int,
    ) {
        buffer[offset] = (value ushr 8).toByte()
        buffer[offset + 1] = value.toByte()
    }

    private fun writeI32(
        buffer: ByteArray,
        offset: Int,
        value: Int,
    ) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }

    private const val SOCKET_NAME = "cleverestricky-backend.v1"
    private const val IPC_VERSION = 1
    private const val HEADER_BYTES = 16
    private const val FLAG_ERROR = 1
    private const val OP_CRYPTO_BACKUP_ENCRYPT = 21
    private const val OP_CRYPTO_BACKUP_DECRYPT = 22
    private const val OP_KEYBOX_PARSE = 23
    private const val OP_KEYBOX_FILE_PARSE = 24
    private const val OP_BACKEND_PING = 28
    private const val BACKEND_HANDSHAKE_VERSION = 1
    private const val BACKEND_PING_REQUEST_BYTES = 1 + BackendAuth.TOKEN_BYTES
    private const val BACKEND_PING_RESPONSE_BYTES = 19
    private const val BACKEND_STATUS_BYTES = 1
    private const val MAX_BACKEND_ERROR_BYTES = 256
    private const val IO_TIMEOUT_MS = 60_000
    private const val MAX_STARTUP_WAIT_MS = 30_000L
    private const val STARTUP_RETRY_INITIAL_MS = 25L
    private const val STARTUP_RETRY_MAX_MS = 500L
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val MAX_EMPTY_READS = 16
    private const val MAX_PASSWORD_BYTES = 4 * 1024
    private const val MAX_BACKUP_PLAINTEXT_BYTES = 32 * 1024 * 1024
    private const val MAX_BACKUP_WIRE_BYTES = MAX_BACKUP_PLAINTEXT_BYTES + 64
    private const val MAX_BACKUP_RESPONSE_BYTES = MAX_BACKUP_WIRE_BYTES
    private const val MAX_KEYBOX_FILE_NAME_BYTES = 255
    private const val KEYBOX_FILE_SCOPE_CONFIG_ROOT = 0
    private const val KEYBOX_FILE_SCOPE_DIRECTORY = 1
    private const val MAX_BACKEND_REQUEST_BYTES = MAX_BACKUP_WIRE_BYTES + MAX_PASSWORD_BYTES + 2
    private const val ANDROID_AID_NOBODY = 9999
    private const val ANDROID_GID_NOBODY = 9999
    private val IPC_MAGIC = byteArrayOf('C'.code.toByte(), 'T'.code.toByte(), 'I'.code.toByte(), 'P'.code.toByte())
}
