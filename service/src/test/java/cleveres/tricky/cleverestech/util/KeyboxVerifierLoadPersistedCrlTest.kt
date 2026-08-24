package cleveres.tricky.cleverestech.util

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method
import cleveres.tricky.cleverestech.Logger

class KeyboxVerifierLoadPersistedCrlTest {

    private lateinit var tempDir: File
    private lateinit var cacheFile: File
    private lateinit var loadPersistedCrlLockedMethod: Method
    private lateinit var originalLoggerImpl: Logger.LogImpl

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("ct-test-cache").toFile()
        KeyboxVerifier.setCacheRootForTesting(tempDir)
        cacheFile = File(tempDir, "attestation_status_cache.json")

        loadPersistedCrlLockedMethod = KeyboxVerifier::class.java.getDeclaredMethod("loadPersistedCrlLocked", Long::class.java)
        loadPersistedCrlLockedMethod.isAccessible = true

        // Mock Logger to avoid android.util.Log calls in tests
        originalLoggerImpl = Logger::class.java.getDeclaredField("impl").apply { isAccessible = true }.get(Logger) as Logger.LogImpl
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
            override fun i(tag: String, msg: String) {}
            override fun w(tag: String, msg: String) {}
        })
    }

    @After
    fun tearDown() {
        Logger.setImpl(originalLoggerImpl)
        KeyboxVerifier.resetCacheRootForTesting()
        tempDir.deleteRecursively()
    }

    private fun invokeLoadPersistedCrlLocked(now: Long): Pair<ByteArray, Long>? {
        @Suppress("UNCHECKED_CAST")
        return loadPersistedCrlLockedMethod.invoke(KeyboxVerifier, now) as Pair<ByteArray, Long>?
    }

    @Test
    fun `loadPersistedCrlLocked returns null when file does not exist`() {
        val result = invokeLoadPersistedCrlLocked(System.currentTimeMillis())
        assertNull(result)
    }

    @Test
    fun `loadPersistedCrlLocked returns null when file is a directory`() {
        cacheFile.mkdir()
        val result = invokeLoadPersistedCrlLocked(System.currentTimeMillis())
        assertNull(result)
    }

    @Test
    fun `loadPersistedCrlLocked returns valid data when file is valid`() {
        val data = "valid crl data".toByteArray()
        cacheFile.writeBytes(data)

        val modified = cacheFile.lastModified()
        val now = modified + 1000L // 1 second later

        val result = invokeLoadPersistedCrlLocked(now)

        assertEquals(modified, result?.second)
        assertArrayEquals(data, result?.first)
    }

    @Test
    fun `loadPersistedCrlLocked returns null when file is empty`() {
        cacheFile.writeBytes(ByteArray(0))

        val modified = cacheFile.lastModified()
        val now = modified + 1000L

        val result = invokeLoadPersistedCrlLocked(now)

        assertNull(result)
    }

    @Test
    fun `loadPersistedCrlLocked returns null when file is larger than MAX_CRL_BYTES`() {
        val maxBytes = 8 * 1024 * 1024

        cacheFile.outputStream().use { os ->
            os.write(ByteArray(maxBytes + 1))
        }

        val modified = cacheFile.lastModified()
        val now = modified + 1000L

        val result = invokeLoadPersistedCrlLocked(now)

        assertNull(result)
    }

    @Test
    fun `loadPersistedCrlLocked returns null when modified time is zero or negative`() {
        val data = "test data".toByteArray()
        cacheFile.writeBytes(data)
        cacheFile.setLastModified(0L)

        val modified = cacheFile.lastModified()
        val now = modified + 1000L

        if (modified <= 0L) {
            val result = invokeLoadPersistedCrlLocked(now)
            assertNull(result)
        }
    }

    @Test
    fun `loadPersistedCrlLocked returns null when age is negative (modified in future)`() {
        val data = "future file".toByteArray()
        cacheFile.writeBytes(data)

        val modified = cacheFile.lastModified()
        val now = modified - 1000L // 'now' is in the past compared to modified time

        val result = invokeLoadPersistedCrlLocked(now)

        assertNull(result)
    }

    @Test
    fun `loadPersistedCrlLocked returns null when age exceeds CACHE_TTL`() {
        val data = "old file".toByteArray()
        cacheFile.writeBytes(data)

        val modified = cacheFile.lastModified()
        val cacheTtl = 24 * 60 * 60 * 1000L // 24 hours
        val now = modified + cacheTtl // Exactly CACHE_TTL

        val result = invokeLoadPersistedCrlLocked(now)

        assertNull(result)

        // Also test CACHE_TTL + 1
        val result2 = invokeLoadPersistedCrlLocked(now + 1)
        assertNull(result2)
    }

    @Test
    fun `loadPersistedCrlLocked returns null on read failure`() {
        val data = "valid crl data".toByteArray()
        cacheFile.writeBytes(data)

        // Remove read permission
        try {
            Files.setPosixFilePermissions(cacheFile.toPath(), emptySet())
        } catch (e: UnsupportedOperationException) {
            // Posix permissions not supported, cannot test
            return
        }

        val modified = cacheFile.lastModified()
        val now = modified + 1000L

        val result = invokeLoadPersistedCrlLocked(now)

        assertNull(result)
    }
}
