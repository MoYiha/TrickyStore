package cleveres.tricky.cleverestech

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongBoxBinderRoutingTest {
    @Test
    fun `root keystore does not intercept or remap StrongBox discovery`() {
        val source = source("KeystoreInterceptor.kt")

        assertFalse(source.contains("getSecurityLevelTransaction"))
        assertFalse(source.contains("returned == strongBoxTarget"))
        assertFalse(source.contains("writeStrongBinder(currentTeeTarget)"))
        assertFalse(source.contains("requestedSecurityLevel(data) == SecurityLevel.STRONGBOX"))
        assertFalse(source.contains("ServiceSpecificException(ErrorCode.HARDWARE_TYPE_UNAVAILABLE)"))
        assertTrue(source.contains("validTransactCodes(getKeyEntryTransaction)"))
    }

    @Test
    fun `StrongBox child binder remains completely unhooked and platform owned`() {
        val source = source("KeystoreInterceptor.kt")
        val teeLookup = source.indexOf("ks.getSecurityLevel(SecurityLevel.TRUSTED_ENVIRONMENT)")
        val teeRegistration = source.indexOf("tee.asBinder(),", teeLookup)

        assertTrue(teeLookup >= 0)
        assertTrue(teeRegistration > teeLookup)
        assertFalse(source.contains("ks.getSecurityLevel(SecurityLevel.STRONGBOX)"))
        assertFalse(source.contains("strongBox.asBinder()"))
        assertFalse(source.contains("strongBoxInterceptor"))
        assertFalse(source.contains("strongBoxTarget"))
        assertTrue(source.contains("StrongBox remains platform-owned"))
    }

    @Test
    fun `non TEE getKeyEntry exits before cache hashing or certificate parsing`() {
        val source = source("KeystoreInterceptor.kt")
        val metadataRead = source.indexOf("val metadata = response?.metadata")
        val levelGate =
            source.indexOf(
                "metadata.keySecurityLevel != SecurityLevel.TRUSTED_ENVIRONMENT",
                metadataRead,
            )
        val cacheLookup = source.indexOf("CertHack.applyCachedCertificateChain(metadata)", levelGate)
        val chainRead = source.indexOf("val originalChain = Utils.getCertificateChain(response)", cacheLookup)
        val gateBody = source.substring(levelGate, cacheLookup)

        assertTrue(metadataRead >= 0)
        assertTrue(levelGate > metadataRead)
        assertTrue(cacheLookup > levelGate)
        assertTrue(chainRead > cacheLookup)
        assertTrue(gateBody.contains("p.recycle()"))
        assertTrue(gateBody.contains("return Skip"))
    }

    @Test
    fun `security level interceptor is used only for TEE generateKey`() {
        val keystore = source("KeystoreInterceptor.kt")
        val interceptor = source("SecurityLevelInterceptor.kt")
        val teeLookup = keystore.indexOf("ks.getSecurityLevel(SecurityLevel.TRUSTED_ENVIRONMENT)")
        val interceptorCreation = keystore.indexOf("SecurityLevelInterceptor()", teeLookup)
        val generateKeyGate = interceptor.indexOf("code == generateKeyTransaction")
        val backendGate = interceptor.indexOf("CertHack.canHack()", generateKeyGate)
        val policyGate = interceptor.indexOf("Config.needHack(callingUid)", backendGate)

        assertTrue(interceptorCreation > teeLookup)
        assertTrue(generateKeyGate >= 0)
        assertTrue(backendGate > generateKeyGate)
        assertTrue(policyGate > backendGate)
        assertFalse(interceptor.contains("allowGenericReplacement"))
    }

    private fun source(name: String): String =
        File(
            locateRoot(),
            "service/src/main/java/cleveres/tricky/cleverestech/$name",
        ).readText()

    private fun locateRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(6) {
            if (File(current, "service").isDirectory && File(current, "rust").isDirectory) return current
            current = current.parentFile ?: return@repeat
        }
        error("Repository root not found")
    }
}
