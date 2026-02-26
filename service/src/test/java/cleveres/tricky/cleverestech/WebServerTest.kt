package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.UUID

class WebServerTest {

    @Test
    fun testListKeyboxes() {
        // Setup temp directory
        val tempDir = File(System.getProperty("java.io.tmpdir"), "webserver_test_${UUID.randomUUID()}")
        tempDir.mkdirs()
        val keyboxDir = File(tempDir, "keyboxes")
        keyboxDir.mkdirs()

        // Create dummy keyboxes
        File(keyboxDir, "test1.xml").createNewFile()
        File(keyboxDir, "test2.xml").createNewFile()
        File(keyboxDir, "ignore.txt").createNewFile()

        // Init WebServer with no-op permission setter
        val server = WebServer(0, tempDir) { _, _ -> }

        // Create dummy session
        val session = object : IHTTPSession {
            override fun execute() {}
            override fun getCookies() = server.CookieHandler(HashMap())
            override fun getHeaders() = hashMapOf("host" to "localhost")
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = Method.GET
            override fun getParms(): Map<String, String> {
                val map = HashMap<String, String>()
                map["token"] = server.token // Auth token
                return map
            }
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/keyboxes"
            override fun parseBody(files: Map<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
            override fun getParameters(): Map<String, List<String>> = HashMap()
        }

        val response = server.serve(session)
        val jsonStr = inputStreamToString(response.data)

        // Assertions
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertTrue("JSON should contain test1.xml", jsonStr.contains("test1.xml"))
        assertTrue("JSON should contain test2.xml", jsonStr.contains("test2.xml"))
        assertTrue("JSON should NOT contain ignore.txt", !jsonStr.contains("ignore.txt"))

        // Cleanup
        tempDir.deleteRecursively()
    }

    @Test
    fun testFrontendContainsKeyboxPicker() {
        // Setup temp directory
        val tempDir = File(System.getProperty("java.io.tmpdir"), "webserver_test_${UUID.randomUUID()}")
        tempDir.mkdirs()

        // Init WebServer
        val server = WebServer(0, tempDir) { _, _ -> }

        // Create dummy session
        val session = object : IHTTPSession {
            override fun execute() {}
            override fun getCookies() = server.CookieHandler(HashMap())
            override fun getHeaders() = hashMapOf("host" to "localhost")
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = Method.GET
            override fun getParms(): Map<String, String> {
                 val map = HashMap<String, String>()
                 map["token"] = server.token
                 return map
            }
            override fun getQueryParameterString() = ""
            override fun getUri() = "/"
            override fun parseBody(files: Map<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
            override fun getParameters(): Map<String, List<String>> = HashMap()
        }

        val response = server.serve(session)
        val html = inputStreamToString(response.data)

        // Assertions
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        // The implementation uses <input type="text" id="appKeybox" list="keyboxList" ...>
        assertTrue("HTML should contain <input type=\"text\" id=\"appKeybox\"", html.contains("<input type=\"text\" id=\"appKeybox\""))

        // Cleanup
        tempDir.deleteRecursively()
    }

    // Add a test for verify_keyboxes to ensure no runtime errors with the lambda logic
    @Test
    fun testVerifyKeyboxesEndpoint() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "webserver_verify_test_${UUID.randomUUID()}")
        tempDir.mkdirs()
        File(tempDir, "keyboxes").mkdirs()

        val server = WebServer(0, tempDir) { _, _ -> }

        val session = object : IHTTPSession {
            override fun execute() {}
            override fun getCookies() = server.CookieHandler(HashMap())
            override fun getHeaders() = hashMapOf("host" to "localhost", "content-length" to "0") // Must add content-length for POST
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = Method.POST
            override fun getParms(): Map<String, String> {
                val map = HashMap<String, String>()
                map["token"] = server.token
                return map
            }
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/verify_keyboxes"
            override fun parseBody(files: Map<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
            override fun getParameters(): Map<String, List<String>> = HashMap()
        }

        // Just check that it doesn't crash with MethodNotFound or similar.
        // It might return OK or Error depending on network (CRL fetch) or missing files, but shouldn't throw RuntimeException.
        val response = server.serve(session)

        // We expect either OK (with JSON) or INTERNAL_ERROR (if verify fails)
        // But importantly, it exercises the new lambda call path.
        val body = inputStreamToString(response.data)
        println("Verify response: ${response.status} $body")

        assertTrue("Response status should be OK or INTERNAL_ERROR",
            response.status == NanoHTTPD.Response.Status.OK || response.status == NanoHTTPD.Response.Status.INTERNAL_ERROR)

        tempDir.deleteRecursively()
    }

    private fun inputStreamToString(inputStream: InputStream?): String {
        return inputStream?.bufferedReader()?.use { it.readText() } ?: ""
    }
}
