package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WebServerPermissionsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: WebServer
    private lateinit var configDir: File

    @Before
    fun setUp() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
            override fun i(tag: String, msg: String) {}
        })

        // Mock SecureFile implementation for tests
        SecureFile.impl = object : SecureFileOperations {
            override fun writeText(file: File, content: String) {
                file.writeText(content)
            }
            override fun mkdirs(file: File, mode: Int) {
                file.mkdirs()
            }
            override fun touch(file: File, mode: Int) {
                if (!file.exists()) file.createNewFile()
            }
        }

        configDir = tempFolder.newFolder("config")
        server = WebServer(0, configDir)
    }

    @Test
    fun testSaveAndLoadAppConfigWithPermissions() {
        // Prepare data
        val rules = JSONArray()
        val rule1 = JSONObject()
        rule1.put("package", "com.example.app")
        rule1.put("template", "pixel5")
        rule1.put("keybox", "kb1.xml")
        val perms1 = JSONArray()
        perms1.put("CONTACTS")
        perms1.put("MEDIA")
        rule1.put("permissions", perms1)
        rules.put(rule1)

        val rule2 = JSONObject()
        rule2.put("package", "com.example.other")
        rule2.put("template", "null")
        rule2.put("keybox", "null")
        val perms2 = JSONArray() // Empty
        rule2.put("permissions", perms2)
        rules.put(rule2)

        // Simulate POST request to SAVE
        val sessionPost = MockSession(
            NanoHTTPD.Method.POST,
            "/api/app_config_structured",
            mapOf("data" to rules.toString()),
            server.token,
            mapOf("content-length" to "100") // Dummy length
        )
        val responsePost = server.serve(sessionPost)
        assertEquals(NanoHTTPD.Response.Status.OK, responsePost.status)

        // Verify file content
        val configFile = File(configDir, "app_config")
        assertTrue(configFile.exists())
        val content = configFile.readText()

        assertTrue("Content should contain permissions (CONTACTS,MEDIA). Content: $content", content.contains("CONTACTS,MEDIA"))

        // Simulate GET request to LOAD
        val sessionGet = MockSession(
            NanoHTTPD.Method.GET,
            "/api/app_config_structured",
            emptyMap(),
            server.token
        )
        val responseGet = server.serve(sessionGet)
        assertEquals(NanoHTTPD.Response.Status.OK, responseGet.status)

        val jsonStr = responseGet.data.bufferedReader().readText()
        val jsonArr = JSONArray(jsonStr)

        var foundRule1 = false
        for (i in 0 until jsonArr.length()) {
            val obj = jsonArr.getJSONObject(i)
            if (obj.getString("package") == "com.example.app") {
                foundRule1 = true
                assertTrue("Should have permissions field", obj.has("permissions"))
                val p = obj.getJSONArray("permissions")
                assertEquals(2, p.length())
                val pList = listOf(p.getString(0), p.getString(1))
                assertTrue(pList.contains("CONTACTS"))
                assertTrue(pList.contains("MEDIA"))
            }
        }
        assertTrue("Should find rule1", foundRule1)
    }

    // Mock Session class
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
