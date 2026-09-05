package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CertificateBackendProvenanceTest {
    private val rewriteCalls = AtomicInteger(0)

    @Before
    fun setUp() {
        CertificateBackend.resetForTesting()
        rewriteCalls.set(0)
        CertificateBackend.rewriteOverride = {
            rewriteCalls.incrementAndGet()
            byteArrayOf(0x30, 0x00)
        }
    }

    @After
    fun tearDown() {
        CertificateBackend.resetForTesting()
    }

    @Test
    fun `TEE source remains eligible for bounded replacement signing`() {
        CertificateBackend.inspectionOverride = {
            inspection(
                CertificateBackend.SECURITY_LEVEL_TEE,
                CertificateBackend.SECURITY_LEVEL_TEE,
            )
        }

        val rewritten = rewrite()

        assertArrayEquals(byteArrayOf(0x30, 0x00), rewritten)
        assertEquals(1, rewriteCalls.get())
    }

    @Test
    fun `StrongBox source preserves genuine chain instead of cross signing`() {
        CertificateBackend.inspectionOverride = {
            inspection(
                CertificateBackend.SECURITY_LEVEL_STRONGBOX,
                CertificateBackend.SECURITY_LEVEL_STRONGBOX,
            )
        }

        assertNull(rewrite())
        assertEquals(0, rewriteCalls.get())
    }

    @Test
    fun `mixed attestation and KeyMint levels fail closed`() {
        val combinations =
            listOf(
                CertificateBackend.SECURITY_LEVEL_TEE to CertificateBackend.SECURITY_LEVEL_STRONGBOX,
                CertificateBackend.SECURITY_LEVEL_STRONGBOX to CertificateBackend.SECURITY_LEVEL_TEE,
                CertificateBackend.SECURITY_LEVEL_SOFTWARE to CertificateBackend.SECURITY_LEVEL_TEE,
            )
        for ((attestation, keymint) in combinations) {
            CertificateBackend.inspectionOverride = { inspection(attestation, keymint) }
            assertNull(rewrite())
        }
        assertEquals(0, rewriteCalls.get())
    }

    @Test
    fun `provenance inspection boot digests are wiped after decision`() {
        val key = ByteArray(32) { 0x41 }
        val hash = ByteArray(32) { 0x42 }
        CertificateBackend.inspectionOverride = {
            inspection(
                CertificateBackend.SECURITY_LEVEL_STRONGBOX,
                CertificateBackend.SECURITY_LEVEL_STRONGBOX,
                key,
                hash,
            )
        }

        assertNull(rewrite())
        assertTrue(key.all { it == 0.toByte() })
        assertTrue(hash.all { it == 0.toByte() })
    }

    private fun inspection(
        attestationLevel: Int,
        keymintLevel: Int,
        bootKey: ByteArray? = null,
        bootHash: ByteArray? = null,
    ) =
        CertificateBackend.Inspection(
            systemPatch = null,
            vendorPatch = null,
            bootPatch = null,
            presentIdMask = 0,
            supportsModuleHash = false,
            originalBootKey = bootKey,
            originalBootHash = bootHash,
            attestationSecurityLevel = attestationLevel,
            keymintSecurityLevel = keymintLevel,
        )

    private fun rewrite(): ByteArray? =
        CertificateBackend.rewrite(
            genuineLeafDer = byteArrayOf(0x30, 0x00),
            keyId = ByteArray(16) { 0x11 },
            signingAlgorithm = CertificateBackend.SIGNING_EC_P256_SHA256,
            systemDisposition = CertificateBackend.PATCH_KEEP,
            systemValue = 0,
            vendorDisposition = CertificateBackend.PATCH_KEEP,
            vendorValue = 0,
            bootDisposition = CertificateBackend.PATCH_KEEP,
            bootValue = 0,
            idOverrides = emptyMap(),
            moduleHash = null,
            verifiedBootKey = ByteArray(32) { 0x21 },
            verifiedBootHash = ByteArray(32) { 0x31 },
        )
}
