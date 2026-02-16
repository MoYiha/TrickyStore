package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WebServerExtendedPermissionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: WebServer
    private lateinit var configDir: File

    @Before
    fun setUp() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) { println("DEBUG: $tag: $msg") }
            override fun e(tag: String, msg: String) { println("ERROR: $tag: $msg") }
            override fun e(tag: String, msg: String, t: Throwable?) { println("ERROR: $tag: $msg"); t?.printStackTrace() }
            override fun i(tag: String, msg: String) { println("INFO: $tag: $msg") }
        })

        SecureFile.impl = object : SecureFileOperations {
            override fun writeText(file: File, content: String) {
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            override fun mkdirs(file: File, mode: Int) {
                file.mkdirs()
            }
            override fun touch(file: File, mode: Int) {
                file.parentFile?.mkdirs()
                if (!file.exists()) file.createNewFile()
            }
        }

        configDir = tempFolder.newFolder("config")
        server = WebServer(0, configDir)
    }

    @Test
    fun testSaveAppConfigWithStandardAndroidPermission() {
        // Prepare data with a standard Android permission (lowercase, dots)
        val rules = JSONArray()
        val rule = JSONObject()
        rule.put("package", "com.example.app")
        rule.put("template", "null")
        rule.put("keybox", "null")
        val perms = JSONArray()
        perms.put("android.permission.INTERNET")
        rule.put("permissions", perms)
        rules.put(rule)

        // Simulate POST request
        val sessionPost = MockSession(
            NanoHTTPD.Method.POST,
            "/api/app_config_structured",
            mapOf("data" to rules.toString()),
            server.token,
            mapOf("content-length" to "100") // Dummy length required by WebServer
        )
        val responsePost = server.serve(sessionPost)

        // This is expected to fail with BAD_REQUEST currently
        assertEquals("Standard Android permission should be accepted", NanoHTTPD.Response.Status.OK, responsePost.status)
    }

    inner class MockSession(
        private val method: NanoHTTPD.Method,
        private val uri: String,
        private val params: Map<String, String>,
        private val token: String,
        private val headers: Map<String, String> = emptyMap()
    ) : NanoHTTPD.IHTTPSession {
        override fun execute() {}
        override fun getCookies() = server.CookieHandler(HashMap())
        override fun getHeaders(): Map<String, String> {
            val h = HashMap<String, String>()
            h["host"] = "localhost"
            h["x-auth-token"] = token
            h.putAll(headers)
            return h
        }
        override fun getInputStream() = null
        override fun getMethod() = method
        override fun getParms() = params
        override fun getQueryParameterString() = ""
        override fun getUri() = uri
        override fun parseBody(files: Map<String, String>?) {}
        override fun getRemoteIpAddress() = "127.0.0.1"
        override fun getRemoteHostName() = "localhost"
        override fun getParameters() = emptyMap<String, List<String>>()
    }
}
