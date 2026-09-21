package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WebServerClipboardTest {
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

    private fun compactView(text: String): String =
        text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*([{}:;,()=+<>|&?!*/-])\\s*"), "${'$'}1")
            .replace(";}", "}")

    private fun containsLoose(
        html: String,
        needle: String,
    ): Boolean = compactView(html).contains(compactView(needle))

    @Test
    fun testClipboardFunctionSignature() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        assertTrue("copyToClipboard signature invalid", containsLoose(html, "function copyToClipboard(text, msg, btn)"))
        assertTrue("Missing success logic", containsLoose(html, "btn.innerText = 'Copied'"))
        assertTrue("Missing timeout logic", containsLoose(html, "setTimeout(() => btn.innerHTML = originalHtml, 2000)"))
    }

    @Test
    fun testClipboardButtonCalls() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        val regex = Regex("copyToClipboard\\s*\\(\\s*'[^']+'\\s*,\\s*'[^']+'\\s*,\\s*this\\s*\\)")
        assertTrue("No clipboard call with 'this' found", regex.containsMatchIn(html))

        val binancePart = "copyToClipboard('114574830','Copied Binance ID',this)"
        assertTrue("Binance button missing 'this'", html.contains(binancePart))
        assertTrue("Copy buttons should use text labels", html.contains(">Copy</button>"))
    }
}
