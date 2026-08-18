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
    fun `measured getKeyEntry serves encoded cache before X509 chain parsing`() {
        val root = locateRoot()
        val source =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt",
            ).readText()
        val postTransact = source.indexOf("override fun onPostTransact")
        val responseRead = source.indexOf("val response = reply.readTypedObject", postTransact)
        val encodedCache =
            source.indexOf("CertHack.applyCachedCertificateChain(response.metadata)", responseRead)
        val chainRead =
            source.indexOf("val originalChain = Utils.getCertificateChain(response)", encodedCache)

        assertTrue(postTransact >= 0)
        assertTrue(responseRead > postTransact)
        assertTrue(encodedCache > responseRead)
        assertTrue(chainRead > encodedCache)
    }

    @Test
    fun `cached getKeyEntry applies raw replacement bytes without certificate reencoding`() {
        val root = locateRoot()
        val source =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java",
            ).readText()
        val method = source.indexOf("public static boolean applyCachedCertificateChain")
        val methodEnd = source.indexOf("public static Certificate[] getCachedCertificateChain", method)
        val body = source.substring(method, methodEnd)

        assertTrue(method >= 0)
        assertTrue(methodEnd > method)
        assertTrue(body.contains("new CacheKey(metadata.certificate)"))
        assertTrue(body.contains("cached.applyTo(metadata)"))
        assertFalse(body.contains("CERTIFICATE_FACTORY"))
        assertFalse(body.contains("getEncoded()"))
        assertFalse(body.contains("CertificateBackend"))
    }

    @Test
    fun `uncached non-attested getKeyEntry leaf is rejected locally after cache lookup`() {
        val root = locateRoot()
        val source =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java",
            ).readText()
        val method = source.indexOf("public static Certificate[] hackCertificateChain")
        val cacheLookup = source.indexOf("CachedCertificateChain cached = cache.get(cacheKey)", method)
        val localExtensionGuard =
            source.indexOf("!Utils.hasAndroidAttestationExtension(caList[0])", cacheLookup)
        val backendInspect =
            source.indexOf("inspection = CertificateBackend.inspect(leafEncoded)", localExtensionGuard)
        val backendRewrite =
            source.indexOf("byte[] rewrittenDer = CertificateBackend.rewrite", localExtensionGuard)

        assertTrue(method >= 0)
        assertTrue(cacheLookup > method)
        assertTrue(localExtensionGuard > cacheLookup)
        assertTrue(backendInspect > localExtensionGuard)
        assertTrue(backendRewrite > localExtensionGuard)
    }

    @Test
    fun `completed rewrite cache retains encoded leaf and issuer bytes`() {
        val root = locateRoot()
        val source =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java",
            ).readText()
        val rewrite = source.indexOf("byte[] rewrittenDer = CertificateBackend.rewrite")
        val issuerEncoding = source.indexOf("byte[] issuerChainEncoded = Utils.encodeIssuerChain(result)", rewrite)
        val completed =
            source.indexOf("new CachedCertificateChain(result, rewrittenDer, issuerChainEncoded)", issuerEncoding)
        val cachePut = source.indexOf("cache.put(cacheKey, completed)", completed)

        assertTrue(rewrite >= 0)
        assertTrue(issuerEncoding > rewrite)
        assertTrue(completed > issuerEncoding)
        assertTrue(cachePut > completed)
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
