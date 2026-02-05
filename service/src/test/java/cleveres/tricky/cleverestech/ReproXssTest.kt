package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import java.io.File
import java.io.InputStream
import java.util.UUID

class ReproXssTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var webServer: WebServer
    private lateinit var configDir: File
    private lateinit var originalSecureFileImpl: SecureFileOperations

    @Before
    fun setUp() {
        configDir = tempFolder.newFolder("config")

        originalSecureFileImpl = SecureFile.impl
        SecureFile.impl = object : SecureFileOperations {
            override fun writeText(file: File, content: String) {
                file.writeText(content)
            }
        }

        // Initialize other files to avoid errors
        File(configDir, "target.txt").createNewFile()
    }

    @Test
    fun testStoredXssViaApiSave() {
        webServer = WebServer(8080, configDir)

        // 1. Attack: Use /api/save to write a malicious app_config
        // This bypasses the validation in /api/app_config_structured POST handler
        val maliciousPackage = "<svg/onload=alert(1)>"
        val maliciousContent = "$maliciousPackage null null"

        val saveSession = object : NanoHTTPD.IHTTPSession {
            override fun execute() {}
            override fun getCookies() = null
            override fun getHeaders() = mapOf("content-length" to "100") // Dummy
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = NanoHTTPD.Method.POST
            override fun getParms() = mapOf("token" to webServer.token, "filename" to "app_config", "content" to maliciousContent)
            override fun getParameters() = emptyMap<String, List<String>>()
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/save"
            override fun parseBody(files: MutableMap<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
        }

        val saveResponse = webServer.serve(saveSession)
        if (saveResponse.status != NanoHTTPD.Response.Status.OK) {
            fail("Failed to save malicious file: ${saveResponse.status}")
        }

        // 2. Victim: Retrieve the config via /api/app_config_structured
        val getSession = object : NanoHTTPD.IHTTPSession {
            override fun execute() {}
            override fun getCookies() = null
            override fun getHeaders() = emptyMap<String, String>()
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = NanoHTTPD.Method.GET
            override fun getParms() = mapOf("token" to webServer.token)
            override fun getParameters() = emptyMap<String, List<String>>()
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/app_config_structured"
            override fun parseBody(files: MutableMap<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
        }

        val getResponse = webServer.serve(getSession)
        val jsonStr = getResponse.data.bufferedReader().use { it.readText() }

        println("JSON Response: $jsonStr")

        val jsonArray = JSONArray(jsonStr)

        // Assert that the malicious package is filtered out
        if (jsonArray.length() != 0) {
            val item = jsonArray.getJSONObject(0)
            val pkg = item.getString("package")
            if (pkg == maliciousPackage) {
                fail("Vulnerability still exists: XSS payload was retrieved via API")
            }
        }

        // If length is 0, it means it was filtered, which is good.
    }

    @org.junit.After
    fun tearDown() {
        SecureFile.impl = originalSecureFileImpl
    }
}
