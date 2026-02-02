package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class WebServerSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: WebServer
    private lateinit var configDir: File

    // Tracking for permission calls
    data class PermissionCall(val path: String, val mode: Int)
    private val permissionCalls = mutableListOf<PermissionCall>()

    @Before
    fun setUp() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) { t?.printStackTrace() }
            override fun i(tag: String, msg: String) {}
        })
        configDir = tempFolder.newFolder("config")
        permissionCalls.clear()

        // Inject mock permission setter
        server = WebServer(0, configDir) { file, mode ->
            permissionCalls.add(PermissionCall(file.absolutePath, mode))
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun testUploadKeyboxPermissions() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/api/upload_keybox?token=$token")

        val filename = "test_keybox.xml"
        val content = "<xml>test</xml>"

        // Ensure keyboxes dir does not exist to test mkdirs permission setting
        val keyboxDir = File(configDir, "keyboxes")
        if (keyboxDir.exists()) keyboxDir.deleteRecursively()

        val postData = "filename=$filename&content=$content"
        val postDataBytes = postData.toByteArray(StandardCharsets.UTF_8)

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.write(postDataBytes)
        conn.outputStream.close()

        val responseCode = conn.responseCode
        assertEquals(200, responseCode)

        val uploadedFile = File(configDir, "keyboxes/$filename")
        assertTrue(uploadedFile.exists())

        // Verify permissions calls
        // 1. Directory creation (if missing) -> 0700 (448)
        var foundDirChmod = false
        for (call in permissionCalls) {
            if (call.path == keyboxDir.absolutePath && call.mode == 448) {
                foundDirChmod = true
            }
        }
        assertTrue("chmod 0700 should be called on the keyboxes directory", foundDirChmod)

        // 2. File creation -> 0600 (384)
        var foundFileChmod = false
        for (call in permissionCalls) {
            if (call.path == uploadedFile.absolutePath && call.mode == 384) {
                foundFileChmod = true
            }
        }
        assertTrue("chmod 0600 should be called on the uploaded file", foundFileChmod)
    }
}
