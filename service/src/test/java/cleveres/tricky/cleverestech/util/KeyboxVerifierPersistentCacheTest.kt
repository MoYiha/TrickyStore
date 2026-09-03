package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CrlBackend
import cleveres.tricky.cleverestech.CrlWire
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyboxVerifierPersistentCacheTest {
    @Before
    fun setUp() {
        CrlBackend.refreshOverride = { CrlWire.Handle(TEST_GENERATION, rawEntryCount = 1, normalizedEntryCount = 4) }
    }

    @After
    fun tearDown() {
        CrlBackend.resetForTesting()
    }

    @Test
    fun freshPersistedCrlSurvivesDaemonRestartWithoutNetwork() {
        val root = Files.createTempDirectory("ct-crl-cache").toFile()
        val requestCount = AtomicInteger(0)
        val crlJson = """{"entries":{"12345":"REVOKED"}}"""
        val server = ServerSocket(0)
        val thread =
            Thread {
                try {
                    val client = server.accept()
                    requestCount.incrementAndGet()
                    val reader = client.inputStream.bufferedReader()
                    var line = reader.readLine()
                    while (line != null && line.isNotEmpty()) line = reader.readLine()
                    val writer = client.outputStream.bufferedWriter()
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: application/json\r\n")
                    writer.write("Content-Length: ${crlJson.toByteArray().size}\r\n")
                    writer.write("\r\n")
                    writer.write(crlJson)
                    writer.flush()
                    client.close()
                } catch (_: Exception) {
                }
            }
        thread.start()

        try {
            KeyboxVerifier.setCacheRootForTesting(root)
            KeyboxVerifier.setCrlUrlForTesting("http://localhost:${server.localPort}")
            val first = KeyboxVerifier.fetchCrl()
            assertNotNull(first)
            assertEquals(TEST_GENERATION, first?.generation)
            assertEquals(4, first?.normalizedEntryCount)
            assertEquals(1, requestCount.get())

            thread.join(2_000)
            server.close()
            KeyboxVerifier.clearMemoryCacheForTesting()

            val second = KeyboxVerifier.fetchCrl()
            assertEquals(first, second)
            assertEquals(1, requestCount.get())
        } finally {
            runCatching { server.close() }
            thread.interrupt()
            KeyboxVerifier.resetCrlUrlForTesting()
            KeyboxVerifier.resetCacheRootForTesting()
            root.deleteRecursively()
        }
    }

    @Test
    fun offlineFallbackLoadsExistingCacheWhenNetworkFails() {
        val root = Files.createTempDirectory("ct-crl-offline").toFile()
        try {
            val cacheFile = File(root, "attestation_status_cache.json")
            cacheFile.writeBytes("""{"entries":{"99999":"REVOKED"}}""".toByteArray())

            KeyboxVerifier.setCacheRootForTesting(root)
            KeyboxVerifier.clearMemoryCacheForTesting()
            // Set URL to an unused loopback port so network fetch fails immediately
            val deadSocket = ServerSocket(0)
            val port = deadSocket.localPort
            deadSocket.close()
            KeyboxVerifier.setCrlUrlForTesting("http://localhost:$port")

            val handle = KeyboxVerifier.fetchCrl()
            assertNotNull("Offline fallback should load persisted cache when network fails", handle)
            assertEquals(TEST_GENERATION, handle?.generation)
        } finally {
            KeyboxVerifier.resetCrlUrlForTesting()
            KeyboxVerifier.resetCacheRootForTesting()
            KeyboxVerifier.clearMemoryCacheForTesting()
            root.deleteRecursively()
        }
    }

    @Test
    fun offlineFallbackServesConcurrentFollowersWhenNetworkFails() {
        val root = Files.createTempDirectory("ct-crl-concurrent").toFile()
        try {
            val cacheFile = File(root, "attestation_status_cache.json")
            cacheFile.writeBytes("""{"entries":{"99999":"REVOKED"}}""".toByteArray())

            KeyboxVerifier.setCacheRootForTesting(root)
            KeyboxVerifier.clearMemoryCacheForTesting()
            val deadSocket = ServerSocket(0)
            val port = deadSocket.localPort
            deadSocket.close()
            KeyboxVerifier.setCrlUrlForTesting("http://localhost:$port")

            val threadCount = 4
            val startGate = java.util.concurrent.CountDownLatch(1)
            val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
            val futures = (1..threadCount).map {
                executor.submit<CrlWire.Handle?> {
                    startGate.await()
                    KeyboxVerifier.fetchCrl()
                }
            }
            startGate.countDown()
            val results = futures.map { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
            executor.shutdown()

            assertTrue("Every concurrent caller must receive a fallback handle", results.all { it != null })
            assertEquals(threadCount, results.filterNotNull().size)
        } finally {
            KeyboxVerifier.resetCrlUrlForTesting()
            KeyboxVerifier.resetCacheRootForTesting()
            KeyboxVerifier.clearMemoryCacheForTesting()
            root.deleteRecursively()
        }
    }

    private companion object {
        const val TEST_GENERATION = 43L
    }
}
