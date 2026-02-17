package cleveres.tricky.cleverestech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class WebServerInstrumentationTest {

    @Test
    fun testWebServerStartsAndServesContent() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configDir = File(appContext.filesDir, "config")
        configDir.mkdirs()

        // Start on a random port
        val server = WebServer(0, configDir)
        server.start()

        try {
            assertTrue("Server should be alive", server.isAlive)
            val port = server.listeningPort
            assertTrue("Port should be greater than 0", port > 0)

            val url = URL("http://localhost:$port/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            assertEquals("Response code should be 200", 200, connection.responseCode)
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            assertTrue("Response should contain app name", content.contains("CleveresTricky"))

        } finally {
            server.stop()
        }
    }
}
