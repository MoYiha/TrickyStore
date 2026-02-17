package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WebServerSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val EC_KEY = """-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIAcPs+YkQGT6EDkaEH6Z9StSR7mQuKnh49K0DVqB/ZxYoAoGCCqGSM49
AwEHoUQDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAO
XHqvCjNpJSbUxgz3SZefi8TWWQ1t32G/1w==
-----END EC PRIVATE KEY-----"""

    private val TEST_CERT = """-----BEGIN CERTIFICATE-----
MIIBfTCCASOgAwIBAgIUBZ47iWGUbx00hmWBPTYkakbXnigwCgYIKoZIzj0EAwIw
FDESMBAGA1UEAwwJVGVzdCBDZXJ0MB4XDTI2MDEyOTIxNTI0M1oXDTI3MDEyNDIx
NTI0M1owFDESMBAGA1UEAwwJVGVzdCBDZXJ0MFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAOXHqv
CjNpJSbUxgz3SZefi8TWWQ1t32G/16NTMFEwHQYDVR0OBBYEFCwifKyDaNaHtKvx
m+0eLn/LZoTaMB8GA1UdIwQYMBaAFCwifKyDaNaHtKvxm+0eLn/LZoTaMA8GA1Ud
EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgT+CWCLXuIN5XY0c3mFN1p1FM
1KAiK9pMwjbHYxNxDmYCIQDXriCpaafMnkJIqGb8UsI5XlkQD0soXYP7hd9ymW/t
qg==
-----END CERTIFICATE-----"""

    private lateinit var server: WebServer
    private lateinit var configDir: File

    // Tracking for permission calls
    data class PermissionCall(val path: String, val mode: Int)
    private val permissionCalls = mutableListOf<PermissionCall>()

    private lateinit var originalSecureFileImpl: SecureFileOperations
    private val secureFileCalls = mutableListOf<File>()

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
        secureFileCalls.clear()

        originalSecureFileImpl = SecureFile.impl
        SecureFile.impl = object : SecureFileOperations {
            override fun writeText(file: File, content: String) {
                file.parentFile?.mkdirs()
                file.writeText(content)
                secureFileCalls.add(file)
            }
            override fun mkdirs(file: File, mode: Int) {
                file.mkdirs()
                permissionCalls.add(PermissionCall(file.absolutePath, mode))
            }
            override fun touch(file: File, mode: Int) {
                file.parentFile?.mkdirs()
                if (!file.exists()) file.createNewFile()
                permissionCalls.add(PermissionCall(file.absolutePath, mode))
            }
        }

        // Inject mock permission setter
        server = WebServer(0, configDir) { file, mode ->
            permissionCalls.add(PermissionCall(file.absolutePath, mode))
        }
        server.start()
    }

    @After
    fun tearDown() {
        SecureFile.impl = originalSecureFileImpl
        server.stop()
    }

    @Test
    fun testUploadKeyboxPermissions() {
        val port = server.listeningPort
        val token = server.token
        val url = URL("http://localhost:$port/api/upload_keybox?token=$token")

        val filename = "test_keybox.xml"
        val content = """<?xml version="1.0"?>
<AndroidAttestation>
<NumberOfKeyboxes>1</NumberOfKeyboxes>
<Keybox>
<Key algorithm="ecdsa">
<PrivateKey>
$EC_KEY
</PrivateKey>
<CertificateChain>
<NumberOfCertificates>1</NumberOfCertificates>
<Certificate>
$TEST_CERT
</Certificate>
</CertificateChain>
</Key>
</Keybox>
</AndroidAttestation>"""

        // Ensure keyboxes dir does not exist to test mkdirs permission setting
        val keyboxDir = File(configDir, "keyboxes")
        if (keyboxDir.exists()) keyboxDir.deleteRecursively()

        val postData = "filename=$filename&content=" + URLEncoder.encode(content, StandardCharsets.UTF_8.name())
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

        // 2. File creation -> Handled by SecureFile
        var foundFileSecureWrite = false
        for (f in secureFileCalls) {
            if (f.absolutePath == uploadedFile.absolutePath) {
                foundFileSecureWrite = true
            }
        }
        assertTrue("SecureFile.writeText should be called on the uploaded file", foundFileSecureWrite)
    }
}
