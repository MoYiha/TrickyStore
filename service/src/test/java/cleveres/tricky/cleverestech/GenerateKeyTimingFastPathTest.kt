package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.Utils
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateKeyTimingFastPathTest {
    @Test
    fun `ordinary x509 leaf has no Android attestation extension`() {
        val certificate =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(
                    ByteArrayInputStream(TestKeyboxFixtures.certificate.toByteArray(Charsets.US_ASCII)),
                )

        assertFalse(Utils.hasAndroidAttestationExtension(certificate))
    }

    @Test
    fun `generateKey rejects non-attested leaf before Rust certificate backend`() {
        val root = locateRoot()
        val source =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt",
            ).readText()
        val postTransact = source.indexOf("override fun onPostTransact")
        val localExtensionGuard =
            source.indexOf("!Utils.hasAndroidAttestationExtension(originalLeaf)", postTransact)
        val backendRewrite = source.indexOf("CertHack.hackCertificateChain", postTransact)

        assertTrue(postTransact >= 0)
        assertTrue(localExtensionGuard > postTransact)
        assertTrue(backendRewrite > localExtensionGuard)
    }

    @Test
    fun `default attested generateKey does not require a Rust inspection round trip`() {
        val root = locateRoot()
        val source =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java",
            ).readText()
        val featureGate =
            source.indexOf("PolicyState.Feature.SECURITY_PATCH, uid")
        val inspectionDecision =
            source.indexOf("boolean needsInspection = needsCapturedPatchLevels", featureGate)
        val conditionalInspection =
            source.indexOf("if (needsInspection)", inspectionDecision)
        val backendInspect =
            source.indexOf("inspection = CertificateBackend.inspect(leafEncoded)", conditionalInspection)
        val configuredIds =
            source.indexOf("? configuredIdOverrides(uid)", backendInspect)
        val backendRewrite =
            source.indexOf("byte[] rewrittenDer = CertificateBackend.rewrite", configuredIds)

        assertTrue(featureGate >= 0)
        assertTrue(inspectionDecision > featureGate)
        assertTrue(conditionalInspection > inspectionDecision)
        assertTrue(backendInspect > conditionalInspection)
        assertTrue(configuredIds > backendInspect)
        assertTrue(backendRewrite > configuredIds)
    }

    @Test
    fun `getKeyEntry rejects non-attested leaf before Rust certificate backend`() {
        val root = locateRoot()
        val source =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt",
            ).readText()
        val postTransact = source.indexOf("override fun onPostTransact")
        val chainRead = source.indexOf("val originalChain = Utils.getCertificateChain(response)", postTransact)
        val localExtensionGuard =
            source.indexOf("!Utils.hasAndroidAttestationExtension(originalLeaf)", chainRead)
        val backendRewrite =
            source.indexOf("CertHack.hackCertificateChain(originalChain, callingUid)", chainRead)

        assertTrue(postTransact >= 0)
        assertTrue(chainRead > postTransact)
        assertTrue(localExtensionGuard > chainRead)
        assertTrue(backendRewrite > localExtensionGuard)
    }

    @Test
    fun `timing fix cannot use synthetic delay equalization`() {
        val root = locateRoot()
        val sources =
            listOf(
                File(
                    root,
                    "service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt",
                ).readText(),
                File(
                    root,
                    "service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt",
                ).readText(),
                File(
                    root,
                    "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java",
                ).readText(),
            ).joinToString("\n")

        assertFalse(sources.contains("Thread.sleep"))
        assertFalse(sources.contains("parkNanos"))
        assertFalse(sources.contains("busyWait"))
    }

    private fun locateRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(6) {
            if (File(current, "service").isDirectory && File(current, "rust").isDirectory) return current
            current = current.parentFile ?: return@repeat
        }
        error("Repository root not found")
    }
}
