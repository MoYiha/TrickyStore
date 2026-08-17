package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CrlBackend
import cleveres.tricky.cleverestech.CrlWire
import cleveres.tricky.cleverestech.KeyboxLoader
import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.RustBackendUnavailableException
import cleveres.tricky.cleverestech.keystore.CertHack
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.cert.X509Certificate

object KeyboxVerifier {
    data class Result(
        val file: File,
        val filename: String,
        val status: Status,
        val details: String,
    )

    enum class Status {
        VALID,
        REVOKED,
        INVALID,
        ERROR,
    }

    private const val DEFAULT_CRL_URL = "https://android.googleapis.com/attestation/status"
    private const val MAX_CRL_BYTES = 8L * 1024 * 1024
    private const val MAX_KEYBOX_XML_BYTES = 10L * 1024 * 1024
    private const val MAX_KEYBOX_FILES = 64
    private const val PERSISTED_CRL_FILE = "attestation_status_cache.json"
    private const val CACHE_TTL = 24 * 60 * 60 * 1000L

    @Volatile
    private var crlUrl = DEFAULT_CRL_URL

    @Volatile
    private var cacheRoot = File("/data/adb/cleverestricky")

    private var cachedCrl: CrlWire.Handle? = null
    private var cachedEtag: String? = null
    private var lastFetchTime: Long = 0
    private val cacheLock = java.util.concurrent.locks.ReentrantLock()

    @androidx.annotation.VisibleForTesting
    fun setCrlUrlForTesting(url: String) {
        require(isAllowedCrlUrl(url, allowLoopbackHttp = true)) { "CRL URL must use HTTPS or loopback HTTP" }
        cacheLock.lock()
        try {
            crlUrl = url
            clearCacheLocked()
        } finally {
            cacheLock.unlock()
        }
    }

    @androidx.annotation.VisibleForTesting
    fun resetCrlUrlForTesting() {
        cacheLock.lock()
        try {
            crlUrl = DEFAULT_CRL_URL
            clearCacheLocked()
        } finally {
            cacheLock.unlock()
        }
    }

    fun configureCacheRoot(configDir: File) {
        cacheLock.lock()
        try {
            cacheRoot = configDir
        } finally {
            cacheLock.unlock()
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setCacheRootForTesting(configDir: File) {
        cacheLock.lock()
        try {
            cacheRoot = configDir
            clearCacheLocked()
        } finally {
            cacheLock.unlock()
        }
    }

    @androidx.annotation.VisibleForTesting
    fun clearMemoryCacheForTesting() {
        cacheLock.lock()
        try {
            clearCacheLocked()
        } finally {
            cacheLock.unlock()
        }
    }

    @androidx.annotation.VisibleForTesting
    fun resetCacheRootForTesting() {
        cacheLock.lock()
        try {
            cacheRoot = File("/data/adb/cleverestricky")
            clearCacheLocked()
        } finally {
            cacheLock.unlock()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun verify(
        configDir: File,
        crlFetcher: () -> CrlWire.Handle? = { fetchCrl() },
    ): List<Result> {
        val results = ArrayList<Result>()
        val crl = crlFetcher()
            ?: return listOf(Result(File(""), "Global", Status.ERROR, "Failed to initialize CRL index"))

        if (!Files.isDirectory(configDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return listOf(Result(File(""), "Global", Status.ERROR, "Config directory not found"))
        }

        val legacyFile = File(configDir, "keybox.xml")
        if (isSafeKeyboxFile(legacyFile)) {
            results.add(checkFile(legacyFile, KeyboxLoader.FileScope.CONFIG_ROOT, "keybox.xml", crl))
        }

        val keyboxDir = File(configDir, "keyboxes")
        if (Files.isDirectory(keyboxDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val files = ArrayList<File>(MAX_KEYBOX_FILES)
            try {
                Files.newDirectoryStream(keyboxDir.toPath()).use { entries ->
                    for (path in entries) {
                        val file = path.toFile()
                        if (!file.name.endsWith(".xml", ignoreCase = true) || !isSafeKeyboxFile(file)) continue
                        if (files.size >= MAX_KEYBOX_FILES) {
                            return listOf(Result(File(""), "Global", Status.ERROR, "Too many keybox files"))
                        }
                        files.add(file)
                    }
                }
            } catch (error: IOException) {
                Logger.e("Failed to scan keybox directory", error)
                return listOf(Result(File(""), "Global", Status.ERROR, "Failed to scan keybox directory"))
            }
            files.sortBy { it.name }
            for (file in files) {
                results.add(checkFile(file, KeyboxLoader.FileScope.KEYBOX_DIRECTORY, file.name, crl))
            }
        }
        return results
    }

    @JvmStatic
    fun fetchCrl(): CrlWire.Handle? {
        val now = System.currentTimeMillis()
        cacheLock.lock()
        try {
            cachedCrl?.let { cached ->
                if (now >= lastFetchTime && now - lastFetchTime < CACHE_TTL) return cached
            }

            loadPersistedCrlLocked(now)?.let { persisted ->
                val (raw, modified) = persisted
                try {
                    val handle = CrlBackend.refresh(raw) ?: return@let
                    cachedCrl = handle
                    lastFetchTime = modified
                    Logger.i("Loaded fresh attestation revocation cache into Rust generation ${handle.generation}")
                    return handle
                } finally {
                    raw.fill(0)
                }
            }

            val requestedUrl = crlUrl
            if (!isAllowedCrlUrl(requestedUrl, allowLoopbackHttp = requestedUrl != DEFAULT_CRL_URL)) {
                Logger.e("Rejected unsafe CRL URL")
                return null
            }

            val connection = URL(requestedUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Accept-Encoding", "identity")
                cachedEtag?.let { connection.setRequestProperty("If-None-Match", it) }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED && cachedCrl != null) {
                    lastFetchTime = now
                    return cachedCrl
                }
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Logger.e("CRL fetch failed with HTTP $responseCode")
                    return null
                }

                val declaredLength = connection.contentLengthLong
                if (declaredLength > MAX_CRL_BYTES) throw IOException("CRL response is too large")
                val raw = BoundedInputStream(connection.inputStream, MAX_CRL_BYTES).use(::readAllBytesBounded)
                try {
                    val handle = CrlBackend.refresh(raw) ?: return null
                    persistCrlLocked(raw)
                    cachedCrl = handle
                    cachedEtag = connection.getHeaderField("ETag")?.take(512)
                    lastFetchTime = now
                    return handle
                } finally {
                    raw.fill(0)
                }
            } catch (error: Exception) {
                Logger.e("Failed to fetch CRL", error)
                return null
            } finally {
                connection.disconnect()
            }
        } finally {
            cacheLock.unlock()
        }
    }

    @JvmStatic
    fun countRevokedKeys(): Int = fetchCrl()?.rawEntryCount ?: -1

    @androidx.annotation.VisibleForTesting
    fun invalidateBackendGenerationForTesting() {
        cacheLock.lock()
        try {
            cachedCrl = null
            lastFetchTime = 0
        } finally {
            cacheLock.unlock()
        }
    }

    private fun isAllowedCrlUrl(
        value: String,
        allowLoopbackHttp: Boolean,
    ): Boolean =
        try {
            val uri = URI(value)
            val loopback = uri.host == "localhost" || uri.host == "127.0.0.1" || uri.host == "::1"
            uri.isAbsolute &&
                uri.rawUserInfo == null &&
                uri.rawFragment == null &&
                (
                    uri.scheme.equals("https", ignoreCase = true) ||
                        (allowLoopbackHttp && loopback && uri.scheme.equals("http", ignoreCase = true))
                )
        } catch (_: Exception) {
            false
        }

    private fun loadPersistedCrlLocked(now: Long): Pair<ByteArray, Long>? {
        val cacheFile = File(cacheRoot, PERSISTED_CRL_FILE)
        val path = cacheFile.toPath()
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        val size = cacheFile.length()
        val modified = cacheFile.lastModified()
        val age = now - modified
        if (size !in 1..MAX_CRL_BYTES || modified <= 0L || age < 0L || age >= CACHE_TTL) return null
        return runCatching {
            BoundedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), MAX_CRL_BYTES)
                .use(::readAllBytesBounded) to modified
        }.onFailure {
            Logger.w("Ignoring invalid persisted attestation revocation cache")
        }.getOrNull()
    }

    private fun persistCrlLocked(rawCrl: ByteArray) {
        runCatching {
            SecureFile.writeBytes(File(cacheRoot, PERSISTED_CRL_FILE), rawCrl)
        }.onFailure {
            Logger.w("Could not persist attestation revocation cache")
        }
    }

    private fun clearCacheLocked() {
        cachedCrl = null
        cachedEtag = null
        lastFetchTime = 0
    }

    private fun checkFile(
        file: File,
        scope: KeyboxLoader.FileScope,
        filename: String,
        crl: CrlWire.Handle,
    ): Result =
        try {
            if (!isSafeKeyboxFile(file)) {
                return Result(file, file.name, Status.ERROR, "Unsafe or oversized keybox file")
            }
            val keyboxes = KeyboxLoader.parseFile(scope, filename)
            if (keyboxes.isEmpty()) {
                return Result(file, file.name, Status.INVALID, "No valid keyboxes found or parse error")
            }

            for (keybox in keyboxes) {
                when (verifyKeybox(keybox, crl)) {
                    Status.REVOKED -> {
                        val chain = keybox.certificates()
                        val serial =
                            if (chain.isNotEmpty() && chain[0] is X509Certificate) {
                                (chain[0] as X509Certificate).serialNumber.toString(16)
                            } else {
                                "unknown"
                            }
                        return Result(file, file.name, Status.REVOKED, "Certificate with SN $serial is revoked")
                    }
                    Status.INVALID -> return Result(file, file.name, Status.INVALID, "Keybox structure is invalid")
                    Status.ERROR -> return Result(file, file.name, Status.ERROR, "Rust CRL backend unavailable")
                    Status.VALID -> Unit
                }
            }
            Result(file, file.name, Status.VALID, "Active (${keyboxes.size} keys)")
        } catch (_: RustBackendUnavailableException) {
            Result(file, file.name, Status.ERROR, "Rust backend unavailable")
        } catch (error: Exception) {
            Result(file, file.name, Status.ERROR, "Error: ${error.javaClass.simpleName}")
        }

    private fun isSafeKeyboxFile(file: File): Boolean =
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            file.length() in 1..MAX_KEYBOX_XML_BYTES

    @JvmStatic
    fun verifyKeybox(
        keybox: CertHack.KeyBox,
        crl: CrlWire.Handle,
    ): Status {
        val certificates = keybox.certificates()
        if (certificates.isEmpty()) return Status.INVALID
        val queries = ArrayList<CrlWire.Query>(certificates.size)
        for (certificate in certificates) {
            val x509 = certificate as? X509Certificate ?: return Status.INVALID
            val serial = x509.serialNumber.toByteArray()
            val spki = x509.publicKey.encoded ?: return Status.INVALID
            if (serial.isEmpty() || spki.isEmpty()) return Status.INVALID
            queries += CrlWire.Query(serial, spki)
        }
        val result = CrlBackend.check(crl.generation, queries) ?: return Status.ERROR
        return if (result.revoked.any { it }) Status.REVOKED else Status.VALID
    }

    @JvmStatic
    fun isRevoked(
        certificate: X509Certificate,
        crl: CrlWire.Handle,
    ): Boolean {
        val serial = certificate.serialNumber.toByteArray()
        val spki = certificate.publicKey.encoded ?: return false
        val result = CrlBackend.check(crl.generation, listOf(CrlWire.Query(serial, spki)))
            ?: throw RustBackendUnavailableException(IOException("CRL generation query failed"))
        return result.revoked.single()
    }

    private fun readAllBytesBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            buffer.fill(0)
            output.reset()
        }
    }

    private class BoundedInputStream(input: InputStream, private val maxBytes: Long) :
        FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int =
            super.read().also { value ->
                if (value >= 0) increment(1)
            }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int =
            super.read(buffer, offset, length).also { bytesRead ->
                if (bytesRead > 0) increment(bytesRead)
            }

        private fun increment(bytesRead: Int) {
            count += bytesRead
            if (count > maxBytes) throw IOException("CRL response exceeds $maxBytes bytes")
        }
    }
}
