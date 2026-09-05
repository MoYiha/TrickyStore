package cleveres.tricky.cleverestech

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongBoxBinderRoutingTest {
    @Test
    fun `root keystore interceptor never remaps StrongBox to TEE`() {
        val source = source("KeystoreInterceptor.kt")

        assertFalse(source.contains("getSecurityLevelTransaction"))
        assertFalse(source.contains("returned == strongBoxTarget"))
        assertFalse(source.contains("writeStrongBinder(currentTeeTarget)"))
        assertTrue(source.contains("validTransactCodes(getKeyEntryTransaction)"))
    }

    @Test
    fun `real StrongBox child binder is registered with generic replacement disabled`() {
        val source = source("KeystoreInterceptor.kt")
        val strongBoxLookup = source.indexOf("ks.getSecurityLevel(SecurityLevel.STRONGBOX)")
        val transparentInterceptor =
            source.indexOf(
                "SecurityLevelInterceptor(allowGenericReplacement = false)",
                strongBoxLookup,
            )
        val strongBoxRegistration =
            source.indexOf("strongBox.asBinder(),", transparentInterceptor)
        val strongBoxTargetCapture =
            source.indexOf("strongBoxTarget = strongBox.asBinder()", strongBoxRegistration)

        assertTrue(strongBoxLookup >= 0)
        assertTrue(transparentInterceptor > strongBoxLookup)
        assertTrue(strongBoxRegistration > transparentInterceptor)
        assertTrue(strongBoxTargetCapture > strongBoxRegistration)
    }

    @Test
    fun `security level interceptor gates generateKey before policy and backend work`() {
        val source = source("SecurityLevelInterceptor.kt")
        val preTransact = source.indexOf("override fun onPreTransact")
        val replacementGate = source.indexOf("allowGenericReplacement &&", preTransact)
        val generateKeyGate = source.indexOf("code == generateKeyTransaction", replacementGate)
        val backendGate = source.indexOf("CertHack.canHack()", generateKeyGate)
        val policyGate = source.indexOf("Config.needHack(callingUid)", backendGate)

        assertTrue(preTransact >= 0)
        assertTrue(replacementGate > preTransact)
        assertTrue(generateKeyGate > replacementGate)
        assertTrue(backendGate > generateKeyGate)
        assertTrue(policyGate > backendGate)
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
