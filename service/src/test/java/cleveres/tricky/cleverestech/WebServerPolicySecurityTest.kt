package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class WebServerPolicySecurityTest {
    private fun session(
        method: NanoHTTPD.Method,
        uri: String,
        contentLength: String = "0",
    ): NanoHTTPD.IHTTPSession =
        MockIHTTPSession(
            uri = uri,
            method = method,
            headers = mapOf("host" to "localhost", "content-length" to contentLength)
        )

    @Test
    fun `policy route retains common tamper protection on native bridge`() {
        val root = Files.createTempDirectory("policy-route-tamper").toFile()
        try {
            val server = WebServer(0, root, isTampered = true)
            val response = server.serveBridge(session(NanoHTTPD.Method.GET, "/api/policy_state"))
            assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `policy route retains common native payload limit`() {
        val root = Files.createTempDirectory("policy-route-size").toFile()
        try {
            val server = WebServer(0, root)
            val response = server.serveBridge(
                session(NanoHTTPD.Method.POST, "/api/policy_state", Long.MAX_VALUE.toString()),
            )
            assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
        } finally {
            root.deleteRecursively()
        }
    }
}
