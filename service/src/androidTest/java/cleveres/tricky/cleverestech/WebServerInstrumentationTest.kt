package cleveres.tricky.cleverestech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class WebServerInstrumentationTest {
    private var root: File? = null

    @After
    fun tearDown() {
        Config.reset()
        root?.deleteRecursively()
    }

    @Test
    fun `native bridge dispatch does not require a TCP listener`() {
        val configDir = newRoot("bridge-no-tcp")
        Config.reset()
        Config.setRootForTesting(configDir)
        PolicyState.initialize(configDir).getOrThrow()
        val server = WebServer(0, configDir, crlFetcher = { emptySet() })
        val bridge = WebUiBridge(server, configDir)

        assertFalse("Production WebUI backend must not need NanoHTTPD.start()", server.isAlive)
        val response = bridgeRequest(bridge, "/api/language")
        assertEquals("Known API route must dispatch through the native bridge", 404, response.getInt("status"))
        assertFalse("Bridge dispatch must not start a TCP listener", server.isAlive)
    }

    @Test
    fun `tampered module blocks trusted bridge API requests`() {
        val configDir = newRoot("bridge-tamper")
        val server = WebServer(0, configDir, isTampered = true)
        val bridge = WebUiBridge(server, configDir)

        val response = bridgeRequest(bridge, "/api/config")
        assertEquals(403, response.getInt("status"))
    }

    private fun bridgeRequest(bridge: WebUiBridge, path: String): JSONObject {
        val request =
            JSONObject()
                .put("version", 1)
                .put("method", "GET")
                .put("path", path)
                .put("parameters", JSONObject())
        return JSONObject(
            String(
                bridge.processRequestBytes(request.toString().toByteArray(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8,
            ),
        )
    }

    private fun newRoot(prefix: String): File {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return Files.createTempDirectory(cache.toPath(), prefix).toFile().also { root = it }
    }
}
