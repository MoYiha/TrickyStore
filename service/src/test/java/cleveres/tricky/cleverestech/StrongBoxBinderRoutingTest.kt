package cleveres.tricky.cleverestech

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongBoxBinderRoutingTest {
    @Test
    fun `root keystore interceptor never remaps StrongBox to TEE`() {
        val source = keystoreInterceptorSource()

        assertFalse(source.contains("getSecurityLevelTransaction"))
        assertFalse(source.contains("returned == strongBoxTarget"))
        assertFalse(source.contains("writeStrongBinder(currentTeeTarget)"))
        assertTrue(source.contains("validTransactCodes(getKeyEntryTransaction)"))
    }

    @Test
    fun `real StrongBox child binder remains independently intercepted`() {
        val source = keystoreInterceptorSource()
        val strongBoxLookup = source.indexOf("ks.getSecurityLevel(SecurityLevel.STRONGBOX)")
        val strongBoxRegistration =
            source.indexOf("strongBox.asBinder(),", strongBoxLookup)
        val strongBoxTargetCapture =
            source.indexOf("strongBoxTarget = strongBox.asBinder()", strongBoxRegistration)

        assertTrue(strongBoxLookup >= 0)
        assertTrue(strongBoxRegistration > strongBoxLookup)
        assertTrue(strongBoxTargetCapture > strongBoxRegistration)
    }

    private fun keystoreInterceptorSource(): String =
        File(
            locateRoot(),
            "service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt",
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
