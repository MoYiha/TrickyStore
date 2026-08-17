package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.DeviceKeyManager
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import cleveres.tricky.cleverestech.util.SecureFile
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

private fun ByteArray.indexOfFrom(
    value: Byte,
    startIndex: Int = 0,
): Int {
    for (index in startIndex.coerceAtLeast(0) until size) {
        if (this[index] == value) return index
    }
    return -1
}

object CboxManager {
    private data class UnlockedEntry(
        val sourceLastModified: Long,
        val sourceSize: Long,
        val keyboxes: List<CertHack.KeyBox>,
    )

    private val unlockedCache = ConcurrentHashMap<String, UnlockedEntry>()
    private val lockedFiles: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val validFilename =
        Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,122}\\.cbox", RegexOption.IGNORE_CASE)

    fun initialize() {
        refresh()
    }

    @Synchronized
    fun refresh() {
        if (KeyboxLoader.consumeFileBackendOutage()) {
            throw RustBackendUnavailableException()
        }

        val directory = Config.keyboxDirectory
        if (!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            unlockedCache.clear()
            lockedFiles.clear()
            return
        }

        val files =
            try {
                listCboxFiles(directory)
            } catch (error: IOException) {
                unlockedCache.clear()
                lockedFiles.clear()
                Logger.e("Failed to scan CBOX directory", error)
                return
            }
        val currentFiles = files.mapTo(HashSet()) { it.name }
        val revoked = if (files.isEmpty()) emptySet() else KeyboxVerifier.fetchCrl()

        for (file in files) {
            val name = file.name
            val current = unlockedCache[name]
            if (revoked != null &&
                current != null &&
                current.sourceLastModified == file.lastModified() &&
                current.sourceSize == file.length() &&
                current.keyboxes.all {
                    KeyboxVerifier.verifyKeybox(it, revoked) == KeyboxVerifier.Status.VALID
                }
            ) {
                lockedFiles.remove(name)
                continue
            }

            unlockedCache.remove(name)
            if (revoked != null) {
                val loaded = loadCached(file, revoked)
                if (loaded != null) {
                    unlockedCache[name] = loaded
                    lockedFiles.remove(name)
                    continue
                }
            }
            lockedFiles.add(name)
        }

        unlockedCache.keys.removeIf { it !in currentFiles }
        lockedFiles.retainAll(currentFiles)
        cleanupOrphanedCaches(directory, currentFiles)
        if (revoked == null && files.isNotEmpty()) {
            Logger.w("CBOX keyboxes remain locked because the revocation list is unavailable")
        }
    }

    @Synchronized
    fun unlock(
        filename: String,
        password: String,
        publicKey: String?,
    ): Boolean {
        if (!validFilename.matches(filename) || password.length !in 1..MAX_PASSWORD_CHARS) return false
        val directory = Config.keyboxDirectory
        val file = File(directory, filename)
        if (!isSafeCbox(file)) return false

        var encryptedBytes: ByteArray? = null
        var sourceDigest: ByteArray? = null
        var verificationDigest: ByteArray? = null
        var payloadXml: ByteArray? = null
        return try {
            encryptedBytes = readCboxBounded(file)
            val initialDigest = MessageDigest.getInstance("SHA-256").digest(encryptedBytes)
            sourceDigest = initialDigest
            val verificationKey = publicKey?.takeUnless { it.isBlank() }
            val payload =
                NativeBackend.openCbox(encryptedBytes, password, verificationKey)
                    ?: run {
                        Logger.e("CBOX decrypt or signature verification failed for $filename")
                        return false
                    }
            payloadXml = payload.xmlContent
            if (verificationKey == null && payload.hasSignature) {
                Logger.e("CBOX signature verification failed for $filename")
                return false
            }

            val parsed = KeyboxLoader.parse(payload.xmlContent.copyOf(), filename)
            val revoked = KeyboxVerifier.fetchCrl() ?: return false
            val verified =
                parsed.filter {
                    KeyboxVerifier.verifyKeybox(it, revoked) == KeyboxVerifier.Status.VALID
                }
            if (verified.isEmpty() || verified.size != parsed.size) {
                Logger.e("CBOX contains an invalid or revoked keybox: $filename")
                return false
            }

            val beforeModified = file.lastModified()
            val beforeSize = file.length()
            val currentDigest = digestFile(file)
            verificationDigest = currentDigest
            val afterModified = file.lastModified()
            val afterSize = file.length()
            if (
                beforeModified != afterModified ||
                beforeSize != afterSize ||
                !MessageDigest.isEqual(initialDigest, currentDigest)
            ) {
                Logger.e("CBOX source changed while it was being unlocked: $filename")
                return false
            }

            writeCache(file, payload.xmlContent, initialDigest)
            unlockedCache[filename] =
                UnlockedEntry(afterModified, afterSize, verified.toList())
            lockedFiles.remove(filename)
            true
        } catch (e: Exception) {
            Logger.e("Failed to unlock CBOX: $filename", e)
            false
        } finally {
            encryptedBytes?.fill(0)
            sourceDigest?.fill(0)
            verificationDigest?.fill(0)
            payloadXml?.fill(0)
        }
    }

    fun getUnlockedKeyboxes(): List<CertHack.KeyBox> =
        unlockedCache.entries
            .sortedBy { it.key }
            .flatMap { it.value.keyboxes }

    fun getLockedFiles(): Set<String> = lockedFiles.toSortedSet()

    fun isLocked(filename: String): Boolean = lockedFiles.contains(filename)

    @Throws(IOException::class)
    private fun listCboxFiles(directory: File): List<File> {
        val files = PriorityQueue<File>(MAX_CBOX_FILES, compareByDescending { it.name })
        Files.newDirectoryStream(directory.toPath()).use { entries ->
            for (path in entries) {
                val file = path.toFile()
                if (!validFilename.matches(file.name) || !isSafeCbox(file)) continue
                if (files.size < MAX_CBOX_FILES) {
                    files.add(file)
                } else if (file.name < requireNotNull(files.peek()).name) {
                    files.poll()
                    files.add(file)
                }
            }
        }
        return files.sortedBy { it.name }
    }

    private fun loadCached(
        file: File,
        revoked: Set<String>,
    ): UnlockedEntry? {
        val cacheFile = cacheFileFor(file)
        if (!Files.isRegularFile(cacheFile.toPath(), LinkOption.NOFOLLOW_LINKS) ||
            cacheFile.length() !in 1..MAX_CACHE_BYTES
        ) {
            return null
        }

        val encrypted = cacheFile.readBytes()
        var decrypted: ByteArray? = null
        var sourceDigest: ByteArray? = null
        var expectedDigestBytes: ByteArray? = null
        try {
            decrypted = DeviceKeyManager.decrypt(encrypted) ?: return null
            val firstNewline = decrypted.indexOfFrom('\n'.code.toByte())
            val secondNewline =
                if (firstNewline >= 0) decrypted.indexOfFrom('\n'.code.toByte(), firstNewline + 1) else -1
            if (firstNewline <= 0 || secondNewline <= firstNewline ||
                String(decrypted, 0, firstNewline, StandardCharsets.US_ASCII) != CACHE_VERSION
            ) {
                throw SecurityException("Unsupported CBOX cache format")
            }

            val expectedDigest =
                String(
                    decrypted,
                    firstNewline + 1,
                    secondNewline - firstNewline - 1,
                    StandardCharsets.US_ASCII,
                )
            sourceDigest = digestFile(file)
            expectedDigestBytes = expectedDigest.toHexBytes()
            if (!MessageDigest.isEqual(expectedDigestBytes, sourceDigest)) {
                throw SecurityException("CBOX cache does not match its source")
            }

            val xmlBytes = decrypted.copyOfRange(secondNewline + 1, decrypted.size)
            val parsed = KeyboxLoader.parse(xmlBytes, file.name)
            val verified =
                parsed.filter {
                    KeyboxVerifier.verifyKeybox(it, revoked) == KeyboxVerifier.Status.VALID
                }
            if (verified.isEmpty() || verified.size != parsed.size) return null
            return UnlockedEntry(file.lastModified(), file.length(), verified.toList())
        } catch (e: RustBackendUnavailableException) {
            Logger.w("Rust backend unavailable; preserving CBOX cache ${cacheFile.name}")
            throw e
        } catch (e: Exception) {
            Logger.e("Ignoring invalid CBOX cache for ${file.name}: ${e.javaClass.simpleName}")
            deleteCacheSafely(cacheFile)
            return null
        } finally {
            encrypted.fill(0)
            decrypted?.fill(0)
            sourceDigest?.fill(0)
            expectedDigestBytes?.fill(0)
        }
    }

    private fun writeCache(
        file: File,
        xml: ByteArray,
        sourceDigest: ByteArray,
    ) {
        val prefix =
            (CACHE_VERSION + "\n" + sourceDigest.toHexStringLowercase() + "\n")
                .toByteArray(StandardCharsets.US_ASCII)
        val plaintext = ByteArray(Math.addExact(prefix.size, xml.size))
        prefix.copyInto(plaintext)
        xml.copyInto(plaintext, prefix.size)
        var encrypted: ByteArray? = null
        try {
            encrypted =
                DeviceKeyManager.encrypt(plaintext)
                    ?: throw IllegalStateException("Device cache encryption is unavailable")
            SecureFile.writeBytes(cacheFileFor(file), encrypted)
        } finally {
            prefix.fill(0)
            plaintext.fill(0)
            encrypted?.fill(0)
        }
    }

    @Throws(IOException::class)
    private fun readCboxBounded(file: File): ByteArray {
        Files.newByteChannel(
            file.toPath(),
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            val size = channel.size()
            if (size !in MIN_CBOX_BYTES..MAX_CBOX_BYTES) {
                throw IOException("CBOX size is outside the supported range")
            }
            val bytes = ByteArray(size.toInt())
            return try {
                val target = ByteBuffer.wrap(bytes)
                var emptyReads = 0
                while (target.hasRemaining()) {
                    val count = channel.read(target)
                    if (count < 0) throw IOException("CBOX ended before its declared size")
                    if (count == 0) {
                        if (++emptyReads > MAX_EMPTY_READS) throw IOException("CBOX read stalled")
                    } else {
                        emptyReads = 0
                    }
                }
                if (channel.size() != size) throw IOException("CBOX size changed while being read")
                bytes
            } catch (error: Throwable) {
                bytes.fill(0)
                throw error
            }
        }
    }

    private fun digestFile(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file.toPath()).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            } finally {
                buffer.fill(0)
            }
        }
        return digest.digest()
    }

    private fun isSafeCbox(file: File): Boolean =
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            file.length() in MIN_CBOX_BYTES..MAX_CBOX_BYTES

    private fun cacheFileFor(file: File) = File(file.parentFile, "${file.name}.cache")

    private fun cleanupOrphanedCaches(
        directory: File,
        currentFiles: Set<String>,
    ) {
        try {
            Files.newDirectoryStream(directory.toPath()) { path ->
                path.fileName.toString().endsWith(".cbox.cache", ignoreCase = true)
            }.use { entries ->
                for (path in entries) {
                    val cache = path.toFile()
                    val sourceName = cache.name.removeSuffix(".cache")
                    if (sourceName !in currentFiles) deleteCacheSafely(cache)
                }
            }
        } catch (error: IOException) {
            Logger.w("Could not scan orphaned CBOX caches")
        }
    }

    private fun deleteCacheSafely(file: File) {
        try {
            Files.deleteIfExists(file.toPath())
        } catch (e: Exception) {
            Logger.w("Could not remove CBOX cache ${file.name}")
        }
    }

    private fun ByteArray.toHexStringLowercase(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.toHexBytes(): ByteArray {
        if (length != SHA256_HEX_CHARS) throw SecurityException("Invalid CBOX digest length")
        val result = ByteArray(SHA256_HEX_CHARS / 2)
        for (index in result.indices) {
            val high =
                this[index * 2].digitToIntOrNull(16)
                    ?: throw SecurityException("Invalid CBOX digest")
            val low =
                this[index * 2 + 1].digitToIntOrNull(16)
                    ?: throw SecurityException("Invalid CBOX digest")
            result[index] = ((high shl 4) or low).toByte()
        }
        return result
    }

    private const val CACHE_VERSION = "CTCB1"
    private const val SHA256_HEX_CHARS = 64
    private const val MIN_CBOX_BYTES = 4L + 4L + 16L + 12L + 16L
    private const val MAX_CBOX_BYTES = 10L * 1024 * 1024 + 36L
    private const val MAX_CACHE_BYTES = 16L * 1024 * 1024
    private const val MAX_CBOX_FILES = 64
    private const val MAX_PASSWORD_CHARS = 1024
    private const val MAX_EMPTY_READS = 16
}
