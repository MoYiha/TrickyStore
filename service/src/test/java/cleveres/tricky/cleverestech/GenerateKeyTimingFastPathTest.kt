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

    private fun locateRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(6) {
            if (File(current, "service").isDirectory && File(current, "rust").isDirectory) return current
            current = current.parentFile ?: return@repeat
        }
        error("Repository root not found")
    }
}
