package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate BootLogic code correctness and safety.
 *
 * Validates property hiding, auto-patch logic, shell execution patterns,
 * and run-once semantics.
 */
class BootLogicSafetyTest {

    private lateinit var bootLogicContent: String

    @Before
    fun setup() {
        bootLogicContent = serviceMainFile("BootLogic.kt").readText()
    }

    // ================================
    // Run-once safety
    // ================================

    @Test
    fun testRunOnceGuard() {
        assertTrue(
            "BootLogic.run() must use AtomicBoolean to ensure run-once semantics",
            bootLogicContent.contains("AtomicBoolean") && bootLogicContent.contains("getAndSet(true)")
        )
    }

    // ================================
    // Shell injection prevention
    // ================================

    @Test
    fun testResetPropUsesArrayExec() {
        // Bug fix: resetProp must use array-based exec to prevent shell injection
        assertTrue(
            "resetProp must use array-based Runtime.exec (not sh -c) to prevent shell injection",
            bootLogicContent.contains("""arrayOf("resetprop", "-n", name, value)""")
        )
    }

    @Test
    fun testResetPropDoesNotUseShDashC() {
        // Verify the old vulnerable pattern is gone from resetProp
        // Search the entire function body by finding the function and its matching brace
        val funcStart = bootLogicContent.indexOf("fun resetProp")
        assertTrue("resetProp function must exist", funcStart >= 0)
        val funcBody = bootLogicContent.substring(funcStart)
        // Extract up to the second closing brace (function close)
        var braceCount = 0
        var endIdx = 0
        for (i in funcBody.indices) {
            if (funcBody[i] == '{') braceCount++
            if (funcBody[i] == '}') braceCount--
            if (braceCount == 0 && i > 0) { endIdx = i; break }
        }
        val resetPropBody = funcBody.substring(0, endIdx + 1)
        assertFalse(
            "resetProp must NOT use exec(\"resetprop...\") via sh -c",
            resetPropBody.contains("exec(\"resetprop")
        )
    }

    @Test
    fun testChmodUsesArrayExec() {
        // Bug fix: chmod must use array-based exec
        assertTrue(
            "chmod calls must use array-based exec, not sh -c string interpolation",
            bootLogicContent.contains("""arrayOf("chmod", "600",""")
        )
    }

    @Test
    fun testRmUsesArrayExec() {
        // Bug fix: rm must use array-based exec
        assertTrue(
            "rm calls must use array-based exec, not sh -c",
            bootLogicContent.contains("""arrayOf("rm", "-f", path)""")
        )
    }

    // ================================
    // Property hiding completeness
    // ================================

    @Test
    fun testHidesVerifiedBootState() {
        assertTrue(
            "Must hide ro.boot.verifiedbootstate as green",
            bootLogicContent.contains("ro.boot.verifiedbootstate") &&
            bootLogicContent.contains("green")
        )
    }

    @Test
    fun testHidesFlashLocked() {
        assertTrue(
            "Must hide ro.boot.flash.locked as 1",
            bootLogicContent.contains("ro.boot.flash.locked") &&
            bootLogicContent.contains("\"1\"")
        )
    }

    @Test
    fun testHidesDebugMode() {
        assertTrue(
            "Must hide ro.debuggable as 0",
            bootLogicContent.contains("ro.debuggable") &&
            bootLogicContent.contains("\"0\"")
        )
    }

    @Test
    fun testHidesVbmetaDeviceState() {
        assertTrue(
            "Must hide ro.boot.vbmeta.device_state as locked",
            bootLogicContent.contains("ro.boot.vbmeta.device_state") &&
            bootLogicContent.contains("locked")
        )
    }

    @Test
    fun testHidesBuildType() {
        assertTrue(
            "Must hide ro.build.type as user",
            bootLogicContent.contains("ro.build.type") &&
            bootLogicContent.contains("\"user\"")
        )
    }

    @Test
    fun testHidesBuildTags() {
        assertTrue(
            "Must hide ro.build.tags as release-keys",
            bootLogicContent.contains("ro.build.tags") &&
            bootLogicContent.contains("release-keys")
        )
    }

    @Test
    fun testHidesBootWarranty() {
        assertTrue(
            "Must hide ro.boot.warranty_bit as 0",
            bootLogicContent.contains("ro.boot.warranty_bit") &&
            bootLogicContent.contains("\"0\"")
        )
    }

    @Test
    fun testHidesOemUnlock() {
        assertTrue(
            "Must hide sys.oem_unlock_allowed as 0",
            bootLogicContent.contains("sys.oem_unlock_allowed") &&
            bootLogicContent.contains("\"0\"")
        )
    }

    // ================================
    // Auto-patch update logic
    // ================================

    @Test
    fun testAutoPatchChecksFlagFile() {
        assertTrue(
            "Auto-patch must check FILE_AUTO_PATCH flag file",
            bootLogicContent.contains("FILE_AUTO_PATCH")
        )
    }

    @Test
    fun testAutoPatchChecksSixMonths() {
        assertTrue(
            "Auto-patch must check if security patch is older than 6 months",
            bootLogicContent.contains("6") && bootLogicContent.contains("MONTHS")
        )
    }

    @Test
    fun testAutoPatchUsesDateFormatter() {
        assertTrue(
            "Auto-patch must use yyyy-MM-dd format for security patches",
            bootLogicContent.contains("yyyy-MM-dd")
        )
    }

    // ================================
    // Magisk 32-bit cleanup
    // ================================

    @Test
    fun testRemoveMagisk32ChecksFlagFile() {
        assertTrue(
            "Magisk32 removal must check FILE_REMOVE_MAGISK32 flag file",
            bootLogicContent.contains("FILE_REMOVE_MAGISK32")
        )
    }

    @Test
    fun testRemoveMagisk32KnownPaths() {
        assertTrue(
            "Must check /debug_ramdisk/magisk32 path",
            bootLogicContent.contains("/debug_ramdisk/magisk32")
        )
        assertTrue(
            "Must check /data/adb/magisk/magisk32 path",
            bootLogicContent.contains("/data/adb/magisk/magisk32")
        )
    }

    // ================================
    // Boot mode hiding
    // ================================

    @Test
    fun testBootModeHiding() {
        assertTrue(
            "Must hide recovery boot mode",
            bootLogicContent.contains("recovery") && bootLogicContent.contains("hideBootMode")
        )
    }

    // ================================
    // Region spoofing
    // ================================

    @Test
    fun testChinaRegionSpoofing() {
        assertTrue(
            "Must support Chinese region spoofing (CN/cn)",
            bootLogicContent.contains("FILE_SPOOF_CN") &&
            bootLogicContent.contains("\"CN\"") &&
            bootLogicContent.contains("\"cn\"")
        )
    }
}
