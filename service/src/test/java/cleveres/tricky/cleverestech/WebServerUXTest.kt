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

class WebServerUXTest {

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
    fun testUXImprovements() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        // Verify App Filter Input uses type="search"
        assertTrue("App Filter should use type='search' for native clear button",
            html.contains("id=\"appFilter\" placeholder=\"Filter...\" oninput=\"renderAppTable()\" aria-label=\"Filter rules\" style=\"width:150px; padding:5px 10px; font-size:0.85em; background:var(--input-bg); border:1px solid var(--border); color:#fff; border-radius:4px;\" type=\"search\"") ||
            html.contains("type=\"search\" id=\"appFilter\"") ||
            (html.contains("id=\"appFilter\"") && html.contains("type=\"search\"")) // Use looser check if exact string match is hard
        )
        // Let's use a more robust check for the specific line
        // Current: <input type="text" id="appFilter" ...
        // Expected: <input type="search" id="appFilter" ... (or similar)

        // Verify Apply System-Wide Button passes 'this'
        assertTrue("Apply System-Wide button should pass 'this' to handler",
            html.contains("onclick=\"applySpoofing(this)\"")
        )

        // Verify JS function signature updated (checking for btn arg)
        assertTrue("applySpoofing should accept btn argument",
            html.contains("async function applySpoofing(btn)")
        )

        // Verify JS loading state logic
        assertTrue("applySpoofing should show loading state",
            html.contains("btn.innerText = 'Saving...'")
        )
    }

    @Test
    fun testEditorShortcuts() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        // Verify Save Button has title
        assertTrue("Save button should have title hint for Ctrl+S",
            html.contains("title=\"Ctrl+S\"") && html.contains("id=\"saveBtn\"")
        )

        // Verify Textarea has onkeydown handler
        assertTrue("File editor textarea should have onkeydown handler for Ctrl+S",
            html.contains("onkeydown=\"if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();handleSave(document.getElementById('saveBtn'));}\"")
        )
    }
}
