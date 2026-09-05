package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CertificateBackendProvenanceTest {
    @Test
    fun `StrongBox provenance is classified before issuer selection and cached as passthrough`() {
        val source =
            File(
                locateRoot(),
                "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java",
            ).readText()
        val method = source.indexOf("public static Certificate[] hackCertificateChain")
        val inspect = source.indexOf("inspection = CertificateBackend.inspect(leafEncoded)", method)
        val attestationGate =
            source.indexOf("inspection.getAttestationSecurityLevel() != CertificateBackend.SECURITY_LEVEL_TEE", inspect)
        val keymintGate =
            source.indexOf("inspection.getKeymintSecurityLevel() != CertificateBackend.SECURITY_LEVEL_TEE", attestationGate)
        val passthrough = source.indexOf("CachedCertificateChain.passthrough()", keymintGate)
        val issuerSelection = source.indexOf("selectKeyboxPool(", passthrough)
        val rewrite = source.indexOf("CertificateBackend.rewrite(", issuerSelection)

        assertTrue(method >= 0)
        assertTrue(inspect > method)
        assertTrue(attestationGate > inspect)
        assertTrue(keymintGate > attestationGate)
        assertTrue(passthrough > keymintGate)
        assertTrue(issuerSelection > passthrough)
        assertTrue(rewrite > issuerSelection)
    }

    @Test
    fun `passthrough cache is marker only and adds no background execution`() {
        val source =
            File(
                locateRoot(),
                "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java",
            ).readText()

        assertTrue(source.contains("this.certificates = null"))
        assertTrue(source.contains("if (passthrough) return;"))
        assertTrue(source.contains("size() > MAX_CERTIFICATE_CACHE_ENTRIES"))
        assertFalse(source.contains("ScheduledExecutor"))
        assertFalse(source.contains("Timer("))
        assertFalse(source.contains("Thread.sleep"))
        assertFalse(source.contains("while (true)"))
    }

    @Test
    fun `certificate backend rewrite does not repeat provenance inspection`() {
        val source =
            File(
                locateRoot(),
                "service/src/main/java/cleveres/tricky/cleverestech/CertificateBackend.kt",
            ).readText()
        val rewrite = source.indexOf("fun rewrite(")
        val decode = source.indexOf("internal fun decodeInspection", rewrite)
        val body = source.substring(rewrite, decode)

        assertFalse(body.contains("inspect(genuineLeafDer)"))
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
