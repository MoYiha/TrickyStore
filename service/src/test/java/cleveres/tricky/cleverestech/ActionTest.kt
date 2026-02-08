package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class ActionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: WebServer
    private lateinit var configDir: File
    private lateinit var originalSecureFileImpl: SecureFileOperations

    private val EC_KEY = "-----BEGIN EC PRIVATE KEY-----\n" +
            "MHcCAQEEIAcPs+YkQGT6EDkaEH6Z9StSR7mQuKnh49K0DVqB/ZxYoAoGCCqGSM49\n" +
            "AwEHoUQDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAO\n" +
            "XHqvCjNpJSbUxgz3SZefi8TWWQ1t32G/1w==\n" +
            "-----END EC PRIVATE KEY-----"

    private val TEST_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBfTCCASOgAwIBAgIUBZ47iWGUbx00hmWBPTYkakbXnigwCgYIKoZIzj0EAwIw\n" +
            "FDESMBAGA1UEAwwJVGVzdCBDZXJ0MB4XDTI2MDEyOTIxNTI0M1oXDTI3MDEyNDIx\n" +
            "NTI0M1owFDESMBAGA1UEAwwJVGVzdCBDZXJ0MFkwEwYHKoZIzj0CAQYIKoZIzj0D\n" +
            "AQcDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAOXHqv\n" +
            "CjNpJSbUxgz3SZefi8TWWQ1t32G/16NTMFEwHQYDVR0OBBYEFCwifKyDaNaHtKvx\n" +
            "m+0eLn/LZoTaMB8GA1UdIwQYMBaAFCwifKyDaNaHtKvxm+0eLn/LZoTaMA8GA1Ud\n" +
            "EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgT+CWCLXuIN5XY0c3mFN1p1FM\n" +
            "1KAiK9pMwjbHYxNxDmYCIQDXriCpaafMnkJIqGb8UsI5XlkQD0soXYP7hd9ymW/t\n" +
            "qg==\n" +
            "-----END CERTIFICATE-----"

    private val VALID_XML = "<?xml version=\"1.0\"?>\n" +
            "<AndroidAttestation>\n" +
            "<NumberOfKeyboxes>1</NumberOfKeyboxes>\n" +
            "<Keybox>\n" +
            "<Key algorithm=\"ecdsa\">\n" +
            "<PrivateKey>\n" + EC_KEY + "\n</PrivateKey>\n" +
            "<CertificateChain>\n" +
            "<NumberOfCertificates>1</NumberOfCertificates>\n" +
            "<Certificate>\n" + TEST_CERT + "\n</Certificate>\n" +
            "</CertificateChain>\n" +
            "</Key>\n" +
            "</Keybox>\n" +
            "</AndroidAttestation>"

    @Before
    fun setUp() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) { println("D/$tag: $msg") }
            override fun e(tag: String, msg: String) { println("E/$tag: $msg") }
            override fun e(tag: String, msg: String, t: Throwable?) { println("E/$tag: $msg"); t?.printStackTrace() }
            override fun i(tag: String, msg: String) { println("I/$tag: $msg") }
        })
        configDir = tempFolder.newFolder("config")

        originalSecureFileImpl = SecureFile.impl
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

        server = WebServer(0, configDir)
        server.start()
        // Reset CertHack
        CertHack.readFromXml(null)
    }

    @After
    fun tearDown() {
        SecureFile.impl = originalSecureFileImpl
        server.stop()
        CertHack.readFromXml(null)
    }

    @Test
    fun testWebServerStartsAndServesConfig() {
        val port = server.listeningPort
        assertTrue(port > 0)
        val token = server.token

        val url = URL("http://localhost:$port/api/config?token=$token")
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)

        val content = conn.inputStream.bufferedReader().readText()
        println("Config response: $content")

        // Initial state: 0 keys
        val json = JSONObject(content)
        assertEquals(0, json.getInt("keybox_count"))
    }

    @Test
    fun testCertHackStatus() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/api/config?token=$token")

        // 1. Valid XML
        CertHack.readFromXml(StringReader(VALID_XML))

        var conn = url.openConnection() as HttpURLConnection
        var content = conn.inputStream.bufferedReader().readText()
        var json = JSONObject(content)
        assertEquals(1, json.getInt("keybox_count"))

        // 2. Invalid XML
        val invalidXml = "<AndroidAttestation><NumberOfKeyboxes>1</NumberOfKeyboxes>INVALID</AndroidAttestation>"
        CertHack.readFromXml(StringReader(invalidXml))

        conn = url.openConnection() as HttpURLConnection
        content = conn.inputStream.bufferedReader().readText()
        json = JSONObject(content)
        assertEquals(0, json.getInt("keybox_count"))
    }

    @Test
    fun testSaveFile() {
        val port = server.listeningPort
        val token = server.token
        // Pass params in URL to avoid body parsing issues in test
        val saveUrl = URL("http://localhost:$port/api/save?token=$token&filename=target.txt&content=TEST_CONTENT")

        val conn = saveUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        // Even with empty body, we need doOutput for POST usually, or just length 0.
        conn.doOutput = true
        conn.outputStream.close()

        assertEquals(200, conn.responseCode)

        val savedFile = File(configDir, "target.txt")
        assertTrue("File should exist", savedFile.exists())
        assertEquals("File content mismatch", "TEST_CONTENT", savedFile.readText())
    }
}
