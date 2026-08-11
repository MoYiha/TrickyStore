package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64

class WebUiBridgeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var configDir: File
    private lateinit var bridge: WebUiBridge

    @Before
    fun setUp() {
        SecureFile.impl = MockSecureFileOperations()
        configDir = tempFolder.newFolder("config")
    }

    @After
    fun tearDown() {
        if (::bridge.isInitialized) bridge.stop()
        SecureFile.impl = SecureFile.DefaultSecureFileOperations()
    }

    @Test
    fun `native request reaches api without network authentication`() {
        bridge = WebUiBridge(WebServer(0, configDir), configDir)
        bridge.start()
        val response = submit("00000000000000000000000000000001", "/api/config")

        assertEquals(200, response.getInt("status"))
        val body = decodeBody(response)
        assertTrue(JSONObject(body).has("files"))
        assertFalse(File(configDir, "webui_bridge/requests/00000000000000000000000000000001.request").exists())
        assertFalse(File(configDir, "webui_bridge/requests/00000000000000000000000000000001.working").exists())
    }

    @Test
    fun `tamper lockdown and unknown fields fail closed`() {
        bridge = WebUiBridge(WebServer(0, configDir, true), configDir)
        bridge.start()
        val blocked = submit("00000000000000000000000000000002", "/api/config")
        assertEquals(403, blocked.getInt("status"))

        val invalid =
            JSONObject()
                .put("version", 1)
                .put("method", "GET")
                .put("path", "/api/config")
                .put("parameters", JSONObject())
                .put("unexpected", true)
        val rejected = submit("00000000000000000000000000000003", invalid)
        assertEquals(400, rejected.getInt("status"))
    }

    @Test
    fun `large responses spill to bounded staging`() {
        bridge = WebUiBridge(WebServer(0, configDir), configDir)
        bridge.start()
        val requestId = "00000000000000000000000000000004"
        val body = ByteArray(300 * 1024) { index -> (index and 0xff).toByte() }
        val response =
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/octet-stream",
                ByteArrayInputStream(body),
                body.size.toLong(),
            )
        val responseFile = File(configDir, "webui_bridge/responses/$requestId.response")
        val method =
            WebUiBridge::class.java.getDeclaredMethod(
                "writeResponse",
                String::class.java,
                File::class.java,
                NanoHTTPD.Response::class.java,
            )
        method.isAccessible = true
        method.invoke(bridge, requestId, responseFile, response)

        val envelope = JSONObject(responseFile.readText())
        assertEquals(body.size, envelope.getInt("size"))
        assertEquals(requestId, envelope.getString("downloadId"))
        assertFalse(envelope.has("body"))
        assertArrayEquals(body, File(configDir, "webui_bridge/staging/$requestId.download").readBytes())
    }

    @Test
    fun `rejected upload request removes its staging file`() {
        bridge = WebUiBridge(WebServer(0, configDir), configDir)
        bridge.start()
        val uploadId = "11111111111111111111111111111111"
        val upload = File(configDir, "webui_bridge/staging/$uploadId.upload")
        upload.writeText("payload")
        val request =
            JSONObject()
                .put("version", 1)
                .put("method", "POST")
                .put("path", "/api/upload_keybox")
                .put("parameters", JSONObject())
                .put("uploadId", uploadId)
                .put("uploadField", 7)

        val response = submit("00000000000000000000000000000005", request)

        assertEquals(400, response.getInt("status"))
        assertFalse(upload.exists())
    }

    private fun submit(
        id: String,
        path: String,
    ): JSONObject =
        submit(
            id,
            JSONObject()
                .put("version", 1)
                .put("method", "GET")
                .put("path", path)
                .put("parameters", JSONObject()),
        )

    private fun submit(
        id: String,
        request: JSONObject,
    ): JSONObject {
        val requestFile = File(configDir, "webui_bridge/requests/$id.request")
        requestFile.writeText(request.toString())
        bridge.processPendingRequests()
        val responseFile = File(configDir, "webui_bridge/responses/$id.response")
        repeat(100) {
            if (responseFile.isFile) return JSONObject(responseFile.readText())
            Thread.sleep(10)
        }
        throw AssertionError("Native bridge response was not published")
    }

    private fun decodeBody(response: JSONObject): String {
        val encoded = response.getString("body")
        val padding = "=".repeat((4 - encoded.length % 4) % 4)
        return String(Base64.getUrlDecoder().decode(encoded + padding), Charsets.UTF_8)
    }
}
