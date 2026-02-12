package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import java.io.File
import java.io.InputStream

class ReproParensBugTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var webServer: WebServer
    private lateinit var configDir: File

    @Before
    fun setUp() {
        configDir = tempFolder.newFolder("config")
        webServer = WebServer(8080, configDir)

        // Mock SecureFile
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
    }

    private fun mockSession(filename: String, content: String): NanoHTTPD.IHTTPSession {
        return object : NanoHTTPD.IHTTPSession {
            override fun execute() {}
            override fun getCookies() = null
            override fun getHeaders() = mapOf("content-length" to "100", "host" to "localhost")
            override fun getInputStream(): InputStream? = null
            override fun getMethod() = NanoHTTPD.Method.POST
            override fun getParms() = mapOf(
                "token" to webServer.token,
                "filename" to filename,
                "content" to content
            )
            override fun getParameters() = emptyMap<String, List<String>>()
            override fun getQueryParameterString() = ""
            override fun getUri() = "/api/save"
            override fun parseBody(files: MutableMap<String, String>?) {}
            override fun getRemoteIpAddress() = "127.0.0.1"
            override fun getRemoteHostName() = "localhost"
        }
    }

    @Test
    fun testSaveModelWithParentheses() {
        // Many devices have model names with parentheses, e.g. "Galaxy S20 (Exynos)"
        val content = "MODEL=Galaxy S20 (Exynos)\n"
        val response = webServer.serve(mockSession("spoof_build_vars", content))

        // This should be allowed, but current validation blocks '(' and ')'
        assertEquals("Should allow parentheses in property values", NanoHTTPD.Response.Status.OK, response.status)
    }
}
