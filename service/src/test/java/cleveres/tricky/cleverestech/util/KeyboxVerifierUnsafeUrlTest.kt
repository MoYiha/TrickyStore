package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CrlBackend
import cleveres.tricky.cleverestech.CrlWire
import cleveres.tricky.cleverestech.Logger
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class KeyboxVerifierUnsafeUrlTest {
    @Before
    fun setUp() {
        KeyboxVerifier.clearMemoryCacheForTesting()
        // Override CrlBackend to fail when parsing invalid JSON (which represents unparseable responses)
        CrlBackend.refreshOverride = { raw ->
            val rawString = String(raw).trimEnd(0.toChar()) // BoundedInputStream padding
            if (rawString.contains("invalid json")) {
                null
            } else {
                CrlWire.Handle(TEST_GENERATION, rawEntryCount = 1, normalizedEntryCount = 4)
            }
        }
        Logger.setImpl(
            object : Logger.LogImpl {
                override fun d(tag: String, msg: String) {}
                override fun e(tag: String, msg: String) {}
                override fun e(tag: String, msg: String, t: Throwable?) {}
                override fun i(tag: String, msg: String) {}
                override fun w(tag: String, msg: String) {}
            },
        )
    }

    @After
    fun tearDown() {
        KeyboxVerifier.resetCrlUrlForTesting()
        CrlBackend.resetForTesting()
    }

    @Test
    fun `fetchCrl rejects unsafe CRL URL and returns null`() {
        // Bypass setCrlUrlForTesting to set an unsafe URL
        val crlUrlField = KeyboxVerifier::class.java.getDeclaredField("crlUrl")
        crlUrlField.isAccessible = true
        crlUrlField.set(KeyboxVerifier, "http://example.com/unsafe")

        val result = KeyboxVerifier.fetchCrl()

        assertNull("fetchCrl should return null for an unsafe URL", result)
    }

    @Test
    fun `fetchCrl ignores unparseable responses`() {
        val server = ServerSocket(0)
        val port = server.localPort

        val thread = Thread {
            try {
                val client = server.accept()
                val reader = client.inputStream.bufferedReader()
                var line = reader.readLine()
                while (line != null && line.isNotEmpty()) {
                    line = reader.readLine()
                }

                val writer = client.outputStream.bufferedWriter()
                writer.write("HTTP/1.1 200 OK\r\n")
                writer.write("Content-Type: application/json\r\n")
                writer.write("\r\n")
                writer.write("invalid json")
                writer.flush()
                client.close()
            } catch (_: Exception) {}
        }
        thread.start()

        try {
            KeyboxVerifier.setCrlUrlForTesting("http://localhost:$port")
            val result = KeyboxVerifier.fetchCrl()
            assertNull("fetchCrl should handle bad json network responses", result)
        } finally {
            thread.interrupt()
            server.close()
        }
    }

    @Test
    fun `fetchCrl ignores server error responses`() {
        val server = ServerSocket(0)
        val port = server.localPort

        val thread = Thread {
            try {
                val client = server.accept()
                val reader = client.inputStream.bufferedReader()
                var line = reader.readLine()
                while (line != null && line.isNotEmpty()) {
                    line = reader.readLine()
                }

                val writer = client.outputStream.bufferedWriter()
                writer.write("HTTP/1.1 500 Internal Server Error\r\n")
                writer.write("\r\n")
                writer.flush()
                client.close()
            } catch (_: Exception) {}
        }
        thread.start()

        try {
            KeyboxVerifier.setCrlUrlForTesting("http://localhost:$port")
            val result = KeyboxVerifier.fetchCrl()
            assertNull("fetchCrl should handle 500 responses", result)
        } finally {
            thread.interrupt()
            server.close()
        }
    }

    private companion object {
        const val TEST_GENERATION = 41L
    }
}
