package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import java.io.InputStream
import java.util.UUID

class WebServerConfigInjectionTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var webServer: WebServer
    private lateinit var configDir: File

    @Before
    fun setUp() {
        configDir = tempFolder.newFolder("config")
        File(configDir, "target.txt").createNewFile()
    }

    @Test
    fun testApiConfigJsonInjection() {
        // 1. Create a custom template with a malicious name
        val maliciousName = "hack\", \"injected\": true, \"dummy\": \""
        val customTemplatesFile = File(configDir, "custom_templates")
        customTemplatesFile.writeText("[$maliciousName]\nMODEL=Hack\n")

        // 2. Update Config
        Config.updateCustomTemplates(customTemplatesFile)

        // 3. Create WebServer
        webServer = WebServer(8080, configDir)

        // 4. Simulate Request
        val session = object : NanoHTTPD.IHTTPSession {
            override fun execute() {}
            override fun getCookies() = null
            override fun getHeaders() = mapOf("host" to "localhost")
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = NanoHTTPD.Method.GET
            override fun getParms() = mapOf("token" to webServer.token)
            override fun getParameters() = emptyMap<String, List<String>>() // Implemented
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/config"
            override fun parseBody(files: MutableMap<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
        }

        val response = webServer.serve(session)
        val jsonStr = response.data.bufferedReader().use { it.readText() }

        println("JSON Response: $jsonStr")

        // 5. Verify JSON validity
        try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            fail("Vulnerability confirmed: JSON parsing failed due to injection: ${e.message}")
        }
    }
}
