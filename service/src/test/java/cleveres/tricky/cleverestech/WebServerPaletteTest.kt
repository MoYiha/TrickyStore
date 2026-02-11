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

class WebServerPaletteTest {

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
    fun testPaletteImprovements() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        // 1. Verify CSS includes button:disabled
        assertTrue("CSS should include button:disabled styling",
            html.contains("button:disabled { opacity: 0.5; cursor: not-allowed; }") ||
            html.contains("textarea:disabled, input:disabled, select:disabled, button:disabled { opacity: 0.5; cursor: not-allowed; }")
        )

        // 2. Verify Add Rule Button has ID and disabled attribute
        // Searching for exact string might be fragile if attributes are reordered, but for now we expect a specific format
        assertTrue("Add Rule button should have ID and be disabled by default",
            html.contains("id=\"btnAddRule\"") && html.contains("disabled") && html.contains(">Add Rule</button>")
        )

        // 3. Verify appPkg input has oninput handler
        assertTrue("appPkg input should have oninput handler",
            html.contains("id=\"appPkg\"") && html.contains("oninput=\"toggleAddButton()\"")
        )

        // 4. Verify toggleAddButton function exists
        assertTrue("toggleAddButton function should exist",
            html.contains("function toggleAddButton()")
        )

        // 5. Verify addAppRule calls toggleAddButton
        assertTrue("addAppRule should call toggleAddButton to reset state",
            html.contains("toggleAddButton();")
        )
    }
}
