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
    fun testHtmlStructure() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        // Verify Title and Badge
        assertTrue("Missing Title", html.contains("<h1>CleveresTricky"))
        assertTrue("Missing Beta Badge", html.contains("BETA</span></h1>"))

        // Verify Tabs
        assertTrue("Missing Dashboard Tab", html.contains("id=\"tab_dashboard\""))
        assertTrue("Missing Spoof Tab", html.contains("id=\"tab_spoof\""))
        assertTrue("Missing Apps Tab", html.contains("id=\"tab_apps\""))

        // Verify Dynamic Island
        assertTrue("Missing Island Container", html.contains("class=\"island-container\""))
        assertTrue("Missing Island", html.contains("id=\"island\""))
        assertTrue("Missing Island Accessibility", html.contains("role=\"status\" aria-live=\"polite\""))
        assertTrue("Missing notify function", html.contains("function notify(msg, type = 'normal')"))
        assertTrue("Missing Remove Button Accessibility", html.contains("aria-label=\"Remove rule for ${'$'}{rule.package}\""))

        // Verify Random Logic
        assertTrue("Missing Randomized Extras Header", html.contains("<h3>System-Wide Spoofing (Global Hardware)</h3>"))
        assertTrue("Missing IMEI Input", html.contains("id=\"inputImei\""))
        assertTrue("Missing IMEI Label", html.contains("<label for=\"inputImei\""))
        assertTrue("Missing IMSI Label", html.contains("<label for=\"inputImsi\""))
        assertTrue("Missing ICCID Label", html.contains("<label for=\"inputIccid\""))
        assertTrue("Missing Serial Label", html.contains("<label for=\"inputSerial\""))
        assertTrue("Missing WiFi MAC Label", html.contains("<label for=\"inputWifiMac\""))
        assertTrue("Missing BT MAC Label", html.contains("<label for=\"inputBtMac\""))
        assertTrue("Missing SIM ISO Input", html.contains("id=\"inputSimIso\""))
        assertTrue("Missing SIM ISO Label", html.contains("<label for=\"inputSimIso\""))
        assertTrue("Missing Operator Label", html.contains("<label for=\"inputSimOp\""))
        assertTrue("Missing Generate Random Button", html.contains("generateRandomIdentity"))

        // Verify Apps Logic
        assertTrue("Missing App Package Input", html.contains("id=\"appPkg\""))
        assertTrue("Missing App Package Label", html.contains("<label for=\"appPkg\""))
        assertTrue("Missing App Template Label", html.contains("<label for=\"appTemplate\""))
        assertTrue("Missing App Keybox Label", html.contains("<label for=\"appKeybox\""))
        assertTrue("Missing Blank Permissions Logic", html.contains("Blank Permissions (Privacy)"))
        assertTrue("Missing Contacts Permission Toggle", html.contains("id=\"permContacts\""))
        assertTrue("Missing Remove Button Accessibility", html.contains("aria-label=\"Remove rule for \${rule.package}\""))
        assertTrue("Missing Empty State", html.contains("No active rules"))

        // Verify Editor
        assertTrue("Missing File Selector", html.contains("id=\"fileSelector\""))

        // Verify Keybox
        assertTrue("Missing Keybox File Picker", html.contains("id=\"kbFilePicker\""))
        assertTrue("Missing Verify Button", html.contains("verifyKeyboxes"))
    }

    @Test
    fun testAccessibilityAttributes() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()

        // Verify Tabs Accessibility
        assertTrue("Missing Tab Role", html.contains("role=\"tab\""))
        assertTrue("Missing Tabindex", html.contains("tabindex=\"0\""))
        assertTrue("Missing Aria Selected", html.contains("aria-selected=\"true\""))
        assertTrue("Missing Key Handler", html.contains("onkeydown=\"handleTabKey"))
        assertTrue("Missing Tab Controls", html.contains("aria-controls=\"dashboard\""))

        // Verify Panels Accessibility
        assertTrue("Missing Tabpanel Role", html.contains("role=\"tabpanel\""))
        assertTrue("Missing Aria Labelledby", html.contains("aria-labelledby=\"tab_dashboard\""))

        // Verify JS helpers
        assertTrue("Missing handleTabKey JS", html.contains("function handleTabKey(e, id)"))
        assertTrue("Missing aria-selected update in switchTab", html.contains("setAttribute('aria-selected'"))
    }
}
