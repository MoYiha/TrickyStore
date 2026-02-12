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
            override fun getHeaders() = mapOf("content-length" to "100", "host" to "localhost") // Dummy
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
        if (saveResponse.status != NanoHTTPD.Response.Status.BAD_REQUEST) {
            fail("Should have rejected malicious file, but got: ${saveResponse.status}")
        }
    }

    @org.junit.After
    fun tearDown() {
        SecureFile.impl = originalSecureFileImpl
    }
}
