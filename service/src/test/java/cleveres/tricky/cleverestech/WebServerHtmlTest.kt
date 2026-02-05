package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WebServerHtmlTest {

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
        configDir = tempFolder.newFolder("config")
        server = WebServer(0, configDir)
        server.start()
        CertHack.readFromXml(null)
    }

    @After
    fun tearDown() {
        server.stop()
        CertHack.readFromXml(null)
    }

    @Test
    fun testHtmlAccessibility() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        // Verify labels for checkboxes
        assertTrue("Missing label for global_mode", html.contains("<label for=\"global_mode\""))
        assertTrue("Missing label for tee_broken_mode", html.contains("<label for=\"tee_broken_mode\""))
        assertTrue("Missing label for rkp_bypass", html.contains("<label for=\"rkp_bypass\""))

        // Verify new labels for App Config
        assertTrue("Missing label for appPkg", html.contains("<label for=\"appPkg\""))
        assertTrue("Missing label for appTemplate", html.contains("<label for=\"appTemplate\""))
        assertTrue("Missing label for appKeybox", html.contains("<label for=\"appKeybox\""))

        // Verify aria-labels
        assertTrue("Missing aria-label for fileSelector", html.contains("aria-label=\"Select configuration file\""))
        assertTrue("Missing aria-label for editor", html.contains("aria-label=\"Configuration editor\""))
        assertTrue("Missing aria-label for templateSelect", html.contains("aria-label=\"Device Identity Selector\""))

        // Verify aria-live
        assertTrue("Missing aria-live for keyboxStatus", html.contains("id=\"keyboxStatus\" aria-live=\"polite\""))

        // Verify save button ID
        assertTrue("Missing id for saveBtn", html.contains("id=\"saveBtn\""))

        // Verify reload button ID
        assertTrue("Missing id for reloadBtn", html.contains("id=\"reloadBtn\""))

        // Verify Toast logic exists
        assertTrue("Missing toast CSS class", html.contains(".toast {"))
        assertTrue("Missing showToast function", html.contains("function showToast(msg)"))

        // Verify Copy Fingerprint button
        assertTrue("Missing Copy button for fingerprint", html.contains("onclick=\"copyFingerprint(this)\""))
        assertTrue("Missing copyFingerprint function", html.contains("function copyFingerprint(btn)"))
        assertTrue("Missing feedback logic in copyFingerprint", html.contains("btn.innerText = 'COPIED!';"))

        // Verify Empty State Logic
        assertTrue("Missing empty state logic in verifyKeyboxes", html.contains("No keyboxes found."))

        // Verify toggle logic
        assertTrue("Missing disabled logic in toggle", html.contains("el.disabled = true;"))

        // Verify File Picker
        assertTrue("Missing file picker input", html.contains("id=\"kbFilePicker\""))
        assertTrue("Missing load from file button", html.contains("LOAD FROM FILE"))
        assertTrue("Missing loadFileContent function", html.contains("function loadFileContent(input)"))

        // Debugging failure
        val hasToast = html.contains("showToast('SETTING UPDATED')")
        if (!hasToast) {
             val idx = html.indexOf("function toggle")
             val snippet = if (idx != -1) html.substring(idx, (idx + 400).coerceAtMost(html.length)) else "Function not found"
             // Replace newlines to make it readable in single line output
             val safeSnippet = snippet.replace("\n", "\\n").replace("\r", "")
             assertTrue("Missing showToast logic. Snippet: $safeSnippet", false)
        }
    }
}
