package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class WebServerUploadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: WebServer
    private lateinit var configDir: File

    @Before
    fun setUp() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) { t?.printStackTrace() }
            override fun i(tag: String, msg: String) {}
        })
        configDir = tempFolder.newFolder("config")

        server = WebServer(0, configDir)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun uploadKeybox(filename: String, content: String): Int {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/api/upload_keybox?token=$token")

        val postData = "filename=$filename&content=$content"
        val postDataBytes = postData.toByteArray(StandardCharsets.UTF_8)

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.write(postDataBytes)
        conn.outputStream.close()

        return conn.responseCode
    }

    @Test
    fun testUploadKeyboxValidFilename() {
        val responseCode = uploadKeybox("valid_keybox.xml", "<xml>ok</xml>")
        assertEquals(200, responseCode)

        val f = File(configDir, "keyboxes/valid_keybox.xml")
        assert(f.exists())
    }

    @Test
    fun testUploadKeyboxInvalidFilenameSpace() {
        val responseCode = uploadKeybox("keybox space.xml", "<xml>bad</xml>")
        assertEquals(400, responseCode)
    }

    @Test
    fun testUploadKeyboxInvalidFilenameSpecialChar() {
        val responseCode = uploadKeybox("keybox!.xml", "<xml>bad</xml>")
        assertEquals(400, responseCode)
    }

    @Test
    fun testUploadKeyboxInvalidFilenameTraversal() {
        // Even if we URL encode it, the server sees the decoded param.
        // But here we send raw string in post body (x-www-form-urlencoded).
        // ".." is dots. "/" is slash.
        // If we send "filename=../foo.xml", regex matches "." but not "/".

        val responseCode = uploadKeybox("../foo.xml", "<xml>bad</xml>")
        assertEquals(400, responseCode)
    }
}
