package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CrlBackend
import cleveres.tricky.cleverestech.CrlWire
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KeyboxVerifierCountRevokedKeysTest {

    private lateinit var tempCacheRoot: File

    @Before
    fun setUp() {
        tempCacheRoot = Files.createTempDirectory("keybox-verifier-count-revoked").toFile()
        KeyboxVerifier.setCacheRootForTesting(tempCacheRoot)
        KeyboxVerifier.clearMemoryCacheForTesting()
    }

    @After
    fun tearDown() {
        KeyboxVerifier.resetCacheRootForTesting()
        KeyboxVerifier.resetCrlUrlForTesting()
        CrlBackend.refreshOverride = null
        tempCacheRoot.deleteRecursively()
    }

    @Test
    fun `countRevokedKeys returns -1 when fetchCrl fails`() {
        // Mock a failure fetching CRL (e.g., using an invalid URL and no cached/persisted data)
        KeyboxVerifier.setCrlUrlForTesting("http://127.0.0.1:9999/invalid")

        // Disable refresh logic since we're testing the failure
        CrlBackend.refreshOverride = { null }

        val count = KeyboxVerifier.countRevokedKeys()
        assertEquals(-1, count)
    }

    @Test
    fun `countRevokedKeys returns normalizedEntryCount when fetchCrl succeeds`() {
        // We'll simulate a successful fetchCrl by creating a fake persisted CRL
        // and mocking CrlBackend.refresh to return a Handle with known entries.
        val expectedCount = 42

        // Mock the CrlBackend refresh to return a specific count
        CrlBackend.refreshOverride = { _ ->
            CrlWire.Handle(
                generation = 1L,
                rawEntryCount = 50,
                normalizedEntryCount = expectedCount
            )
        }

        // We can just populate a fake cache file so loadPersistedCrlLocked finds it
        val cacheFile = File(tempCacheRoot, "attestation_status_cache.json")
        cacheFile.writeBytes(byteArrayOf(1, 2, 3)) // just some dummy content to trigger refresh

        val count = KeyboxVerifier.countRevokedKeys()
        assertEquals(expectedCount, count)
    }
}
