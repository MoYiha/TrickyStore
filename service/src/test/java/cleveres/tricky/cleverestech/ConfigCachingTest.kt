package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.lang.reflect.Method
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job

class ConfigCachingTest {

    private lateinit var tempDir: File
    private lateinit var keyboxFile: File

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

    private val XML_V1 = "<?xml version=\"1.0\"?>\n" +
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

    private val XML_V2 = "<?xml version=\"1.0\"?>\n" +
            "<AndroidAttestation>\n" +
            "<NumberOfKeyboxes>0</NumberOfKeyboxes>\n" +
            "</AndroidAttestation>"

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "cleveres_cache_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        keyboxFile = File(tempDir, "keybox.xml")

        // Reset Config state
        Config.reset()
        Config.setRootForTesting(tempDir)

        // Ensure CertHack is clean
        CertHack.readFromXml(null)

        // Mock Logger to avoid spam
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) { println("E/$tag: $msg") }
            override fun e(tag: String, msg: String, t: Throwable?) { println("E/$tag: $msg"); t?.printStackTrace() }
            override fun i(tag: String, msg: String) { println("I/$tag: $msg") }
        })
    }

    @After
    fun tearDown() {
        Config.reset()
        tempDir.deleteRecursively()
        CertHack.readFromXml(null)
    }

    private fun callUpdateKeyBoxes() {
        val method = Config::class.java.getDeclaredMethod("updateKeyBoxes")
        method.isAccessible = true
        val job = method.invoke(Config) as Job
        runBlocking {
            job.join()
        }
    }

    private fun getCachedLegacyKeyboxes(): List<*> {
        val field = Config::class.java.getDeclaredField("cachedLegacyKeyboxes")
        field.isAccessible = true
        return field.get(Config) as List<*>
    }

    @Test
    fun testCacheAvoidsReloadingSameTimestamp() {
        // 1. Write initial file
        keyboxFile.writeText(XML_V1)
        val initialTime = 10000L
        keyboxFile.setLastModified(initialTime)

        // 2. Load
        callUpdateKeyBoxes()

        // Verify loaded
        val cached1 = getCachedLegacyKeyboxes()
        assertEquals("Should load 1 keybox", 1, cached1.size)

        // 3. Change content but KEEP timestamp
        // We write V2 which has 0 keys.
        keyboxFile.writeText(XML_V2)
        keyboxFile.setLastModified(initialTime)

        // 4. Reload
        callUpdateKeyBoxes()

        // 5. Verify it is NOT reloaded (cached1 should be preserved or new list equal to it)
        val cached2 = getCachedLegacyKeyboxes()
        assertEquals("Should still have 1 keybox (cache hit)", 1, cached2.size)
        // Ensure content is still V1's content (we can check by size)

        // 6. Update timestamp
        val newTime = 20000L
        keyboxFile.setLastModified(newTime)

        // 7. Reload
        callUpdateKeyBoxes()

        // 8. Verify reloaded
        val cached3 = getCachedLegacyKeyboxes()
        assertEquals("Should have 0 keyboxes (reloaded V2)", 0, cached3.size)
    }
}
