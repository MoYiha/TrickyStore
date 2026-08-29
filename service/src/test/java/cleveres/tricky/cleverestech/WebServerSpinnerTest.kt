package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WebServerSpinnerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: WebServer
    private lateinit var configDir: File

    @Before
    fun setUp() {
        Logger.setImpl(
            object : Logger.LogImpl {
                override fun d(
                    tag: String,
                    msg: String,
                ) {}

                override fun e(
                    tag: String,
                    msg: String,
                ) {}

                override fun e(
                    tag: String,
                    msg: String,
                    t: Throwable?,
                ) {}

                override fun i(
                    tag: String,
                    msg: String,
                ) {}
            },
        )
        configDir = tempFolder.newFolder("config")
        server = WebServer(0, configDir)
        server.start()
        ManagedOpaqueKeyOracle.readFromXml(null)
    }

    @After
    fun tearDown() {
        server.stop()
        ManagedOpaqueKeyOracle.readFromXml(null)
    }

    @Test
    fun testSpinnerPresence() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        assertTrue("Missing Spinner CSS class", html.contains(".spinner {"))
        assertTrue("Missing Spinner Animation", html.contains("@keyframes spin"))
        assertTrue("Missing Island Working Spinner Display", html.contains(".island.working .spinner { display: block; }"))
        assertTrue("Missing Spinner Div", html.contains("<div class=\"spinner\"></div>"))
        assertTrue("Missing notifyTimeout cleanup", html.contains("if (notifyTimeout) clearTimeout(notifyTimeout);"))
        assertTrue("Working notifications must not auto dismiss", html.contains("if (type !== 'working') {"))
        assertTrue(
            "Missing bounded error notification timeout",
            html.contains("type === 'error' ? 6000 : 3000"),
        )
        assertTrue("Missing sticky safe-area offset", html.contains("env(safe-area-inset-top)"))
        assertFalse(
            "Mobile tabs must not apply the top safe-area inset twice",
            html.contains(".tabs { padding-top: env(safe-area-inset-top); }"),
        )
    }
}
