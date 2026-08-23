package cleveres.tricky.cleverestech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class WebUiActionInstrumentationTest {
    private var root: File? = null

    @After
    fun tearDown() {
        Config.reset()
        root?.deleteRecursively()
    }

    @Test
    fun `WebUI action backend loads configuration through authenticated native bridge contract`() {
        val configDir = newRoot("webui-action")
        Config.reset()
        Config.setRootForTesting(configDir)
        Config.initialize()
        val bridge = WebUiBridge(WebServer(0, configDir, crlFetcher = { emptySet() }), configDir)

        val request =
            JSONObject()
                .put("version", 1)
                .put("method", "GET")
                .put("path", "/api/config")
                .put("parameters", JSONObject())
        val envelope =
            JSONObject(
                String(
                    bridge.processRequestBytes(request.toString().toByteArray(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8,
                ),
            )

        assertEquals(200, envelope.getInt("status"))
        assertTrue(envelope.getString("mimeType").contains("application/json"))
        val body = String(Base64.getUrlDecoder().decode(envelope.getString("body")), StandardCharsets.UTF_8)
        val config = JSONObject(body)
        assertTrue(config.has("files"))
        assertTrue(config.get("files") is JSONArray)
    }

    @Test
    fun `WebUI bridge refuses action requests outside API namespace`() {
        val configDir = newRoot("webui-action-non-api")
        val bridge = WebUiBridge(WebServer(0, configDir), configDir)
        val request =
            JSONObject()
                .put("version", 1)
                .put("method", "GET")
                .put("path", "/index.html")
                .put("parameters", JSONObject())
        val envelope =
            JSONObject(
                String(
                    bridge.processRequestBytes(request.toString().toByteArray(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8,
                ),
            )
        assertEquals(400, envelope.getInt("status"))
    }

    private fun newRoot(prefix: String): File {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return Files.createTempDirectory(cache.toPath(), prefix).toFile().also { root = it }
    }
}
