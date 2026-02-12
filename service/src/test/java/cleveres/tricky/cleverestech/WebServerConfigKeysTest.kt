package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.util.UUID

class WebServerConfigKeysTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var webServer: WebServer
    private lateinit var configDir: File

    @Before
    fun setUp() {
        configDir = tempFolder.newFolder("config")
        // Create the file so the value should be true
        File(configDir, "auto_beta_fetch").createNewFile()
    }

    @Test
    fun testApiConfigKeysMatchFrontendExpectations() {
        webServer = WebServer(8080, configDir)

        val session = object : NanoHTTPD.IHTTPSession {
            override fun execute() {}
            override fun getCookies() = null
            override fun getHeaders() = mapOf("host" to "localhost")
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = NanoHTTPD.Method.GET
            override fun getParms() = mapOf("token" to webServer.token)
            override fun getParameters() = emptyMap<String, List<String>>()
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/config"
            override fun parseBody(files: MutableMap<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
        }

        val response = webServer.serve(session)
        val jsonStr = response.data.bufferedReader().use { it.readText() }
        val json = JSONObject(jsonStr)

        // The frontend expects "auto_beta_fetch"
        // But the backend sends "auto_beta"

        if (!json.has("auto_beta_fetch")) {
            fail("API response is missing 'auto_beta_fetch'. It has: ${json.keys().asSequence().toList()}")
        }

        assertTrue("auto_beta_fetch should be true", json.getBoolean("auto_beta_fetch"))
    }
}
