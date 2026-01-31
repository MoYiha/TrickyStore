package cleveres.tricky.cleverestech

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import cleveres.tricky.cleverestech.keystore.CertHack
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Unit tests for RKP (Remote Key Provisioning) spoofing functionality.
 * Goal: Verify MEETS_STRONG_INTEGRITY can be achieved.
 */
class RkpInterceptorTest {

    @Before
    fun setup() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) = println("D/$tag: $msg")
            override fun e(tag: String, msg: String) = println("E/$tag: $msg")
            override fun e(tag: String, msg: String, t: Throwable) {
                println("E/$tag: $msg")
                t.printStackTrace()
            }
            override fun i(tag: String, msg: String) = println("I/$tag: $msg")
        })
    }

    @Test
    fun testMacedPublicKeyGeneration() {
        // Generate EC P-256 key pair
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGen.generateKeyPair()
        
        // Generate MacedPublicKey
        val macedKey = CertHack.generateMacedPublicKey(keyPair)
        
        assertNotNull("MacedPublicKey should not be null", macedKey)
        assertTrue("MacedPublicKey should have content", macedKey!!.isNotEmpty())
        
        // Verify COSE_Mac0 header (0x84 = array of 4)
        assertEquals("Should start with COSE_Mac0 array header", 0x84.toByte(), macedKey[0])
        
        println("MacedPublicKey generated successfully, size=${macedKey.size}")
    }

    @Test
    fun testCertificateRequestResponseGeneration() {
        // Generate some test public keys
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGen.generateKeyPair()
        
        val macedKey = CertHack.generateMacedPublicKey(keyPair)
        assertNotNull(macedKey)
        
        val publicKeys = listOf(macedKey!!)
        val challenge = "test_challenge_12345".toByteArray()
        val deviceInfo = CertHack.createDeviceInfoCbor("google", "Google", "generic", "Pixel", "generic")
        assertNotNull(deviceInfo)
        
        // Generate certificate request response
        val response = CertHack.createCertificateRequestResponse(publicKeys, challenge, deviceInfo!!)
        
        assertNotNull("CertificateRequestResponse should not be null", response)
        assertTrue("Response should have content", response!!.isNotEmpty())
        
        // Verify CBOR array header (0x84 = array of 4)
        assertEquals("Should start with CBOR array header", 0x84.toByte(), response[0])
        
        println("CertificateRequestResponse generated successfully, size=${response.size}")
    }

    @Test
    fun testDeviceInfoCborGeneration() {
        val brand = "google"
        val manufacturer = "Google"
        val product = "husky"
        val model = "Pixel 8 Pro"
        val device = "husky"
        
        val deviceInfo = CertHack.createDeviceInfoCbor(brand, manufacturer, product, model, device)
        
        assertNotNull("DeviceInfo should not be null", deviceInfo)
        assertTrue("DeviceInfo should have content", deviceInfo!!.isNotEmpty())
        
        // Verify CBOR map header (0xAA = map of 10 items)
        assertEquals("Should start with CBOR map header", 0xAA.toByte(), deviceInfo[0])
        
        // Verify content contains expected values
        val content = String(deviceInfo, Charsets.UTF_8)
        assertTrue("Should contain brand", content.contains(brand))
        assertTrue("Should contain vb_state", content.contains("vb_state"))
        assertTrue("Should contain green", content.contains("green"))
        
        println("DeviceInfo CBOR generated successfully, size=${deviceInfo.size}")
    }

    @Test
    fun testDeviceInfoWithNullValues() {
        // Test with null values - should use defaults
        val deviceInfo = CertHack.createDeviceInfoCbor(null, null, null, null, null)
        
        assertNotNull("DeviceInfo should not be null even with null inputs", deviceInfo)
        assertTrue("DeviceInfo should have content", deviceInfo!!.isNotEmpty())
        
        // Verify it uses defaults
        val content = String(deviceInfo, Charsets.UTF_8)
        assertTrue("Should contain default brand 'google'", content.contains("google"))
        
        println("DeviceInfo with defaults generated successfully")
    }
}
