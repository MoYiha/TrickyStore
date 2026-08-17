package cleveres.tricky.cleverestech.util

import android.net.LocalSocket
import android.net.LocalSocketAddress
import cleveres.tricky.cleverestech.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption

/** Thin Android adapter for descriptor-relative mutations owned by the privileged Rust daemon. */
internal class RustSecureFileOperations : SecureFileOperations {
    override fun writeText(
        file: File,
        content: String,
    ) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        try {
            writeBytes(file, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    override fun writeBytes(
        file: File,
        content: ByteArray,
    ) {
        require(content.size <= MAX_FILE_BYTES) { "File exceeds the Rust broker size limit" }
        transact(ACTION_WRITE, file, content)
    }

    override fun writeStream(
        file: File,
        inputStream: InputStream,
        limit: Long,
    ) {
        require(limit in -1L..MAX_FILE_BYTES.toLong()) { "Invalid Rust broker stream limit" }
        val effectiveLimit = if (limit < 0) MAX_FILE_BYTES else limit.toInt()
        val output = ByteArrayOutputStream(minOf(effectiveLimit, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytes: ByteArray? = null
        try {
            var total = 0
            var emptyReads = 0
            while (true) {
                val count = inputStream.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    if (++emptyReads > MAX_EMPTY_READS) throw IOException("Input stream stalled")
                    continue
                }
                emptyReads = 0
                total = Math.addExact(total, count)
                if (total > effectiveLimit) throw IOException("File exceeds the configured stream limit")
                output.write(buffer, 0, count)
            }
            bytes = output.toByteArray()
            writeBytes(file, bytes)
        } catch (error: ArithmeticException) {
            throw IOException("File size overflow", error)
        } finally {
            buffer.fill(0)
            bytes?.fill(0)
            output.reset()
        }
    }

    override fun mkdirs(
        file: File,
        mode: Int,
    ) {
        require(mode == DIRECTORY_MODE) { "Rust broker only accepts private config directories" }
        if (file.absolutePath == CONFIG_ROOT) {
            transactEncoded(ACTION_ROOT_VALIDATE, EMPTY_BYTES, EMPTY_BYTES)
            return
        }
        transact(ACTION_MKDIR, file, EMPTY_BYTES)
    }

    override fun touch(
        file: File,
        mode: Int,
    ) {
        require(mode == FILE_MODE) { "Rust broker only accepts private config files" }
        transact(ACTION_TOUCH, file, EMPTY_BYTES)
    }

    private fun transact(
        action: Int,
        file: File,
        content: ByteArray,
    ) {
        val path = relativePath(file)
        val pathBytes = path.toByteArray(Charsets.UTF_8)
        try {
            transactEncoded(action, pathBytes, content)
        } finally {
            pathBytes.fill(0)
        }
    }

    private fun transactEncoded(
        action: Int,
        pathBytes: ByteArray,
        content: ByteArray,
    ) {
        require(pathBytes.size <= MAX_RELATIVE_PATH_BYTES)
        require(action == ACTION_ROOT_VALIDATE || pathBytes.isNotEmpty())
        require(action != ACTION_ROOT_VALIDATE || pathBytes.isEmpty())
        val payloadLength = Math.addExact(Math.addExact(3, pathBytes.size), content.size)
        require(payloadLength <= MAX_REQUEST_BYTES)
        LocalSocket().use { socket ->
            connectVerified(socket)
            val output = socket.outputStream
            writeHeader(output, payloadLength)
            output.write(action)
            output.write((pathBytes.size ushr 8) and 0xff)
            output.write(pathBytes.size and 0xff)
            if (pathBytes.isNotEmpty()) output.write(pathBytes)
            if (content.isNotEmpty()) output.write(content)
            output.flush()

            val input = socket.inputStream
            val header = ByteArray(HEADER_BYTES)
            try {
                readFully(input, header)
                validateResponseHeader(header)
                val responseLength = readU32(header, 12)
                if (responseLength > MAX_RESPONSE_BYTES) throw IOException("Rust file response exceeds bound")
                val response = ByteArray(responseLength.toInt())
                try {
                    readFully(input, response)
                    if (readI32(header, 8) != 0 || !response.contentEquals(OK_BYTES)) {
                        throw IOException("Rust file operation was rejected")
                    }
                } finally {
                    response.fill(0)
                }
            } finally {
                header.fill(0)
            }
        }
    }

    private fun connectVerified(socket: LocalSocket) {
        socket.connect(LocalSocketAddress(FILE_SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
        val peer = socket.peerCredentials
        if (peer.uid != 0 || peer.gid != 0 || peer.pid <= 1 || !isExpectedDaemonExecutable(peer.pid)) {
            throw IOException("Unexpected privileged Rust daemon peer")
        }
        socket.setSoTimeout(IO_TIMEOUT_MS)
    }

    private fun isExpectedDaemonExecutable(pid: Int): Boolean {
        val classpath = System.getenv("CLASSPATH") ?: return false
        if (classpath.indexOf(File.pathSeparatorChar) >= 0) return false
        val serviceApk = File(classpath)
        val moduleDir = serviceApk.parentFile ?: return false
        val daemon = File(moduleDir, DAEMON_FILENAME).toPath()
        if (!Files.isRegularFile(daemon, LinkOption.NOFOLLOW_LINKS)) return false
        return runCatching { Files.isSameFile(daemon, File("/proc/$pid/exe").toPath()) }.getOrDefault(false)
    }

    private fun relativePath(file: File): String {
        val absolute = file.absolutePath
        val prefix = "$CONFIG_ROOT/"
        if (!absolute.startsWith(prefix)) throw IOException("File is outside the Rust config root")
        val relative = absolute.substring(prefix.length)
        val components = relative.split('/')
        if (components.isEmpty() || components.size > 2) throw IOException("Config path depth exceeds bound")
        components.forEach(::requireComponent)
        if (components.size == 2 && components[0] != KEYBOX_DIRECTORY) {
            throw IOException("Only the keybox directory may contain child files")
        }
        return components.joinToString("/")
    }

    private fun requireComponent(component: String) {
        if (component.isEmpty() || component == "." || component == ".." ||
            component.toByteArray(Charsets.UTF_8).size > MAX_COMPONENT_BYTES || '\u0000' in component
        ) {
            throw IOException("Invalid config path component")
        }
    }

    private fun writeHeader(
        output: java.io.OutputStream,
        payloadLength: Int,
    ) {
        val header = ByteArray(HEADER_BYTES)
        try {
            IPC_MAGIC.copyInto(header)
            writeU16(header, 4, IPC_VERSION)
            writeU16(header, 6, OP_FILE_WRITE)
            writeI32(header, 8, 0)
            writeI32(header, 12, payloadLength)
            output.write(header)
        } finally {
            header.fill(0)
        }
    }

    private fun validateResponseHeader(header: ByteArray) {
        for (index in IPC_MAGIC.indices) {
            if (header[index] != IPC_MAGIC[index]) throw IOException("Invalid Rust file response magic")
        }
        if (readU16(header, 4) != IPC_VERSION || readU16(header, 6) != OP_FILE_WRITE) {
            throw IOException("Invalid Rust file response header")
        }
        val flags = readI32(header, 8)
        if (flags != 0 && flags != FLAG_ERROR) throw IOException("Invalid Rust file response flags")
    }

    private fun readFully(
        input: InputStream,
        output: ByteArray,
    ) {
        var offset = 0
        var emptyReads = 0
        while (offset < output.size) {
            val count = input.read(output, offset, output.size - offset)
            if (count < 0) throw IOException("Rust file response ended early")
            if (count == 0) {
                if (++emptyReads > MAX_EMPTY_READS) throw IOException("Rust file response stalled")
                continue
            }
            emptyReads = 0
            offset += count
        }
    }

    private fun readU16(
        bytes: ByteArray,
        offset: Int,
    ): Int = ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun readI32(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readU32(
        bytes: ByteArray,
        offset: Int,
    ): Long = readI32(bytes, offset).toLong() and 0xffff_ffffL

    private fun writeU16(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeI32(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private companion object {
        const val CONFIG_ROOT = "/data/adb/cleverestricky"
        const val KEYBOX_DIRECTORY = "keyboxes"
        const val FILE_SOCKET_NAME = "cleverestrickyd.files.v1"
        const val DAEMON_FILENAME = "cleverestrickyd"
        const val IPC_VERSION = 1
        const val OP_FILE_WRITE = 10
        const val FLAG_ERROR = 1
        const val ACTION_WRITE = 0
        const val ACTION_MKDIR = 1
        const val ACTION_TOUCH = 2
        const val ACTION_ROOT_VALIDATE = 3
        const val HEADER_BYTES = 16
        const val FILE_MODE = 384
        const val DIRECTORY_MODE = 448
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val MAX_RELATIVE_PATH_BYTES = 511
        const val MAX_COMPONENT_BYTES = 255
        const val MAX_REQUEST_BYTES = 1 + 2 + MAX_RELATIVE_PATH_BYTES + MAX_FILE_BYTES
        const val MAX_RESPONSE_BYTES = 512L
        const val IO_TIMEOUT_MS = 30_000
        const val MAX_EMPTY_READS = 16
        val IPC_MAGIC = byteArrayOf('C'.code.toByte(), 'T'.code.toByte(), 'I'.code.toByte(), 'P'.code.toByte())
        val OK_BYTES = byteArrayOf('o'.code.toByte(), 'k'.code.toByte())
        val EMPTY_BYTES = ByteArray(0)
    }
}
