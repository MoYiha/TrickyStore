package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.UUID

class WebServerHostValidationTest {

    private fun createSession(server: WebServer, hostHeader: String?): NanoHTTPD.IHTTPSession {
        return object : NanoHTTPD.IHTTPSession {
            override fun execute() {}
            override fun getCookies() = server.CookieHandler(HashMap())
            override fun getHeaders(): Map<String, String> {
                val map = HashMap<String, String>()
                if (hostHeader != null) map["host"] = hostHeader
                if (hostHeader != null) map["origin"] = "http://$hostHeader"
                return map
            }
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = NanoHTTPD.Method.GET
            override fun getParms(): Map<String, String> {
                val map = HashMap<String, String>()
                map["token"] = server.token
                return map
            }
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/config"
            override fun parseBody(files: Map<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
            override fun getParameters(): Map<String, List<String>> = HashMap()
        }
    }

    @Test
    fun testHostHeaderValidation() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "webserver_test_${UUID.randomUUID()}")
        tempDir.mkdirs()

        // Use no-op permission setter
        val server = WebServer(0, tempDir) { _, _ -> }

        // Test Case 1: Valid Host (localhost)
        val sessionLocalhost = createSession(server, "localhost:8080")
        val responseLocalhost = server.serve(sessionLocalhost)
        assertEquals("Should allow localhost", NanoHTTPD.Response.Status.OK, responseLocalhost.status)

        // Test Case 2: Valid Host (127.0.0.1)
        val sessionIp = createSession(server, "127.0.0.1:8080")
        val responseIp = server.serve(sessionIp)
        assertEquals("Should allow 127.0.0.1", NanoHTTPD.Response.Status.OK, responseIp.status)

        // Test Case 3: Invalid Host (attacker.com)
        // This is expected to FAIL currently (return 200 OK)
        // After fix, it should return 403 Forbidden
        val sessionAttacker = createSession(server, "attacker.com")
        val responseAttacker = server.serve(sessionAttacker)
        assertEquals("Should block attacker.com", NanoHTTPD.Response.Status.FORBIDDEN, responseAttacker.status)

        tempDir.deleteRecursively()
    }
}
