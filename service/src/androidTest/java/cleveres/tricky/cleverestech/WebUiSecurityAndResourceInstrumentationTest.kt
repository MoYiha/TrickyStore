package cleveres.tricky.cleverestech

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

/** Android 17 abuse, resource-bound, and broad latency contracts for the native WebUI bridge. */
@RunWith(AndroidJUnit4::class)
class WebUiSecurityAndResourceInstrumentationTest {
    private lateinit var root: File
    private lateinit var bridge: WebUiBridge
    private lateinit var originalSecureFileImpl: SecureFileOperations

    @Before
    fun setUp() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.canonicalFile
        root = Files.createTempDirectory(cache.toPath(), "webui-security-resource").toFile().canonicalFile
        originalSecureFileImpl = SecureFile.impl
        SecureFile.impl = SecureFile.DefaultSecureFileOperations()
        Config.reset()
        KeyboxLoader.activeSetOverride = { true }
        Config.setRootForTesting(root)
        Config.initialize()
        KernelIdentityManager.initialize(root)
        bridge = WebUiBridge(WebServer(0, root, crlFetcher = { emptySet() }), root)
    }

    @After
    fun tearDown() {
        Config.reset()
        SecureFile.impl = originalSecureFileImpl
        root.deleteRecursively()
    }

    @Test
    fun `bridge rejects malformed traversal oversized and parameter abuse before dispatch`() {
        assertEquals(400, decode(bridge.processRequestBytes(byteArrayOf(0xc3.toByte(), 0x28))).status)

        val extraField = baseEnvelope("GET", "/api/config").put("unexpected", true)
        assertEquals(400, process(extraField).status)

        listOf("/api/../config", "/api/\\config", "/api/config\u0000tail").forEach { path ->
            assertEquals("path must be rejected: $path", 400, process(baseEnvelope("GET", path)).status)
        }

        val tooManyParameters = JSONObject()
        repeat(129) { index -> tooManyParameters.put("p$index", JSONArray().put("v")) }
        assertEquals(400, process(baseEnvelope("GET", "/api/config", tooManyParameters)).status)

        val tooManyValues = JSONObject().put("p", JSONArray().apply { repeat(33) { put("v$it") } })
        assertEquals(400, process(baseEnvelope("GET", "/api/config", tooManyValues)).status)

        val invalidUpload =
            baseEnvelope("POST", "/api/restore")
                .put("uploadId", "../../escape")
                .put("uploadField", "file")
        assertEquals(400, process(invalidUpload).status)

        var oversizedRejected = false
        try {
            bridge.processRequestBytes(ByteArray(MAX_BRIDGE_REQUEST_BYTES + 1))
        } catch (_: IllegalArgumentException) {
            oversizedRejected = true
        }
        assertTrue("bridge must reject over-limit request frames before JSON parsing", oversizedRejected)
    }

    @Test
    fun `config symlink cannot redirect writes outside the configuration root`() {
        val outside = File(root.parentFile, "ct-outside-${System.nanoTime()}.txt").canonicalFile
        outside.writeText("sentinel", Charsets.UTF_8)
        val target = File(root, "target.txt")
        Files.deleteIfExists(target.toPath())
        try {
            Files.createSymbolicLink(target.toPath(), outside.toPath())
            val response =
                request(
                    "POST",
                    "/api/save",
                    mapOf("filename" to "target.txt", "content" to "com.example.attack\n"),
                )
            assertEquals(400, response.status)
            assertEquals("sentinel", outside.readText(Charsets.UTF_8))
            assertTrue("destination must remain a symbolic link", Files.isSymbolicLink(target.toPath()))
        } finally {
            Files.deleteIfExists(target.toPath())
            outside.delete()
        }
    }

    @Test
    fun `staged upload symlink is rejected without touching its target`() {
        val staging = File(root, "webui_bridge/staging")
        Files.createDirectories(staging.toPath())
        val outside = File(root.parentFile, "ct-upload-outside-${System.nanoTime()}.bin").canonicalFile
        outside.writeBytes("sentinel".toByteArray(StandardCharsets.UTF_8))
        val uploadId = "abcdef0123456789abcdef0123456789"
        val staged = File(staging, "$uploadId.upload")
        try {
            Files.createSymbolicLink(staged.toPath(), outside.toPath())
            val response =
                request(
                    "POST",
                    "/api/restore",
                    mapOf("pw" to "correct horse battery staple"),
                    uploadId = uploadId,
                    uploadField = "file",
                )
            assertEquals(400, response.status)
            assertEquals("sentinel", outside.readText(Charsets.UTF_8))
            assertTrue("bridge cleanup must never follow and delete an upload symlink", Files.isSymbolicLink(staged.toPath()))
        } finally {
            Files.deleteIfExists(staged.toPath())
            outside.delete()
        }
    }

    @Test
    fun `sustained bridge traffic stays within broad latency fd and staging budgets`() {
        repeat(4) { assertEquals(200, request("GET", "/api/config").status) }

        val fdsBefore = openFdCount()
        val stagedBefore = stagingEntryCount()
        assertTrue("/proc/self/fd must be observable on Android", fdsBefore > 0)

        val started = SystemClock.elapsedRealtimeNanos()
        repeat(64) { index ->
            val response =
                if (index % 2 == 0) {
                    request("GET", "/api/config")
                } else {
                    request("GET", "/api/policy_state")
                }
            assertEquals("iteration $index", 200, response.status)
            assertTrue(
                "normal bridge responses must remain comfortably inside the inline envelope budget",
                response.envelopeBytes <= MAX_NORMAL_ENVELOPE_BYTES,
            )
        }
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L

        val fdsAfter = openFdCount()
        val stagedAfter = stagingEntryCount()
        assertTrue(
            "64 local bridge calls took ${elapsedMs}ms; this is a regression guard, not a microbenchmark",
            elapsedMs < BROAD_LATENCY_BUDGET_MS,
        )
        assertTrue("file descriptors leaked: before=$fdsBefore after=$fdsAfter", fdsAfter <= fdsBefore + MAX_FD_GROWTH)
        assertEquals("read-only bridge traffic must not leak staging files", stagedBefore, stagedAfter)
    }

    private fun request(
        method: String,
        path: String,
        parameters: Map<String, String> = emptyMap(),
        uploadId: String? = null,
        uploadField: String? = null,
    ): BridgeResponse {
        val parameterJson = JSONObject()
        parameters.forEach { (key, value) -> parameterJson.put(key, JSONArray().put(value)) }
        val envelope = baseEnvelope(method, path, parameterJson)
        if (uploadId != null) envelope.put("uploadId", uploadId)
        if (uploadField != null) envelope.put("uploadField", uploadField)
        return process(envelope)
    }

    private fun baseEnvelope(
        method: String,
        path: String,
        parameters: JSONObject = JSONObject(),
    ): JSONObject =
        JSONObject()
            .put("version", 1)
            .put("method", method)
            .put("path", path)
            .put("parameters", parameters)

    private fun process(envelope: JSONObject): BridgeResponse =
        decode(bridge.processRequestBytes(envelope.toString().toByteArray(StandardCharsets.UTF_8)))

    private fun decode(bytes: ByteArray): BridgeResponse {
        val envelope = JSONObject(String(bytes, StandardCharsets.UTF_8))
        val body =
            envelope.optString("body")
                .takeIf { it.isNotEmpty() }
                ?.let { Base64.getUrlDecoder().decode(it) }
                ?: ByteArray(0)
        return BridgeResponse(
            status = envelope.getInt("status"),
            text = String(body, StandardCharsets.UTF_8),
            envelopeBytes = bytes.size,
        )
    }

    private fun openFdCount(): Int = File("/proc/self/fd").list()?.size ?: -1

    private fun stagingEntryCount(): Int = File(root, "webui_bridge/staging").list()?.size ?: 0

    private data class BridgeResponse(
        val status: Int,
        val text: String,
        val envelopeBytes: Int,
    )

    companion object {
        private const val MAX_BRIDGE_REQUEST_BYTES = 1024 * 1024
        private const val MAX_NORMAL_ENVELOPE_BYTES = 64 * 1024
        private const val BROAD_LATENCY_BUDGET_MS = 15_000L
        private const val MAX_FD_GROWTH = 4
    }
}
