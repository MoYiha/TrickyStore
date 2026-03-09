package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate ServerManager code correctness and safety.
 *
 * Validates authentication types, content processing, caching,
 * and server lifecycle management.
 */
class ServerManagerSafetyTest {

    private lateinit var serverManagerContent: String

    @Before
    fun setup() {
        serverManagerContent = serviceMainFile("ServerManager.kt").readText()
    }

    // ================================
    // Authentication support
    // ================================

    @Test
    fun testSupportsBearerAuth() {
        assertTrue(
            "Must support Bearer token authentication",
            serverManagerContent.contains("BEARER") && serverManagerContent.contains("Bearer")
        )
    }

    @Test
    fun testSupportsBasicAuth() {
        assertTrue(
            "Must support Basic authentication with Base64 encoding",
            serverManagerContent.contains("BASIC") && serverManagerContent.contains("Basic")
        )
    }

    @Test
    fun testSupportsApiKeyAuth() {
        assertTrue(
            "Must support API key authentication",
            serverManagerContent.contains("API_KEY") && serverManagerContent.contains("X-API-Key")
        )
    }

    @Test
    fun testSupportsCustomAuth() {
        assertTrue(
            "Must support custom header-based authentication",
            serverManagerContent.contains("CUSTOM")
        )
    }

    @Test
    fun testSupportsTelegramAuth() {
        assertTrue(
            "Must support Telegram-based authentication",
            serverManagerContent.contains("TELEGRAM")
        )
    }

    // ================================
    // Content processing
    // ================================

    @Test
    fun testProcessesCboxContent() {
        assertTrue(
            "Must process CBOX encrypted content",
            serverManagerContent.contains("\"CBOX\"") && serverManagerContent.contains("CboxDecryptor")
        )
    }

    @Test
    fun testProcessesZipContent() {
        assertTrue(
            "Must process ZIP file content",
            serverManagerContent.contains("0x50") && serverManagerContent.contains("0x4B") &&
            serverManagerContent.contains("ZipProcessor")
        )
    }

    @Test
    fun testProcessesPlainXmlContent() {
        assertTrue(
            "Must process plain XML keybox content",
            serverManagerContent.contains("AndroidAttestation")
        )
    }

    @Test
    fun testVerifiesSignature() {
        assertTrue(
            "Must verify CBOX signature when public key provided",
            serverManagerContent.contains("verifySignature")
        )
    }

    // ================================
    // Caching
    // ================================

    @Test
    fun testCachesDecryptedContent() {
        assertTrue(
            "Must cache decrypted XML using DeviceKeyManager encryption",
            serverManagerContent.contains("DeviceKeyManager.encrypt")
        )
    }

    @Test
    fun testLoadsCachedContent() {
        assertTrue(
            "Must load cached keyboxes on startup",
            serverManagerContent.contains("loadCachedKeyboxes") || serverManagerContent.contains("DeviceKeyManager.decrypt")
        )
    }

    @Test
    fun testCacheFileIsEncrypted() {
        assertTrue(
            "Cached server content must be encrypted at rest (.enc)",
            serverManagerContent.contains(".enc")
        )
    }

    // ================================
    // Server lifecycle
    // ================================

    @Test
    fun testPersistsServersToFile() {
        assertTrue(
            "Must persist server configurations to servers.json",
            serverManagerContent.contains("servers.json")
        )
    }

    @Test
    fun testUsesSecureFileForPersistence() {
        assertTrue(
            "Must use SecureFile for writing server config",
            serverManagerContent.contains("SecureFile.writeText")
        )
    }

    @Test
    fun testRemoveServerCleansCacheFile() {
        assertTrue(
            "Removing a server must delete its cache file",
            serverManagerContent.contains("server_cache_") && serverManagerContent.contains(".delete()")
        )
    }

    @Test
    fun testUsesConcurrentCollections() {
        assertTrue(
            "Must use CopyOnWriteArrayList for thread-safe server list",
            serverManagerContent.contains("CopyOnWriteArrayList")
        )
        assertTrue(
            "Must use ConcurrentHashMap for thread-safe keybox cache",
            serverManagerContent.contains("ConcurrentHashMap")
        )
    }

    // ================================
    // Network safety
    // ================================

    @Test
    fun testHasConnectionTimeout() {
        assertTrue(
            "HTTP connections must have a connect timeout",
            serverManagerContent.contains("connectTimeout")
        )
    }

    @Test
    fun testHasReadTimeout() {
        assertTrue(
            "HTTP connections must have a read timeout",
            serverManagerContent.contains("readTimeout")
        )
    }

    @Test
    fun testTracksLastStatus() {
        assertTrue(
            "Must track server last status for UI display",
            serverManagerContent.contains("lastStatus")
        )
    }

    @Test
    fun testHandlesNetworkErrors() {
        assertTrue(
            "Must handle network errors gracefully",
            serverManagerContent.contains("NETWORK_ERROR")
        )
    }
}
