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
        // Single-property resetProp must use array-based exec to prevent shell injection
        assertTrue(
            "resetProp must use array-based Runtime.exec (not sh -c) to prevent shell injection",
            bootLogicContent.contains("""arrayOf("resetprop", "-n", name, value)""")
        )
    }

    @Test
    fun testResetPropDoesNotUseShDashC() {
        // Verify the single-property resetProp function does NOT use sh -c
        val funcStart = bootLogicContent.indexOf("private fun resetProp(name: String, value: String)")
        assertTrue("resetProp function must exist", funcStart >= 0)
        val funcBody = bootLogicContent.substring(funcStart)
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
        assertTrue(
            "chmod calls must use array-based exec, not sh -c string interpolation",
            bootLogicContent.contains("""arrayOf("chmod", "600",""")
        )
    }

    @Test
    fun testBatchResetPropExists() {
        // Performance: properties should be set in batch to avoid spawning 20+ processes
        assertTrue(
            "BootLogic must use resetPropBatch for setting multiple properties at once",
            bootLogicContent.contains("resetPropBatch")
        )
    }

    @Test
    fun testBatchResetPropUsesShellEscape() {
        // Security: batched shell commands must escape values
        assertTrue(
            "resetPropBatch must use shellEscape to prevent injection in batched commands",
            bootLogicContent.contains("shellEscape")
        )
    }

    // ================================
    // FD leak prevention
    // ================================

    @Test
    fun testGetSystemPropertyDrainsErrorStream() {
        // Bug fix: getSystemProperty must drain errorStream to prevent FD exhaustion
        val funcStart = bootLogicContent.indexOf("fun getSystemProperty")
        assertTrue("getSystemProperty function must exist", funcStart >= 0)
        val funcBody = bootLogicContent.substring(funcStart, minOf(funcStart + 500, bootLogicContent.length))
        assertTrue(
            "getSystemProperty must drain errorStream to prevent FD exhaustion",
            funcBody.contains("errorStream")
        )
    }

    @Test
    fun testGetSystemPropertyCallsWaitFor() {
        val funcStart = bootLogicContent.indexOf("fun getSystemProperty")
        assertTrue("getSystemProperty function must exist", funcStart >= 0)
        val funcBody = bootLogicContent.substring(funcStart, minOf(funcStart + 500, bootLogicContent.length))
        assertTrue(
            "getSystemProperty must call waitFor() to avoid zombie processes",
            funcBody.contains("waitFor()")
        )
    }

    @Test
    fun testExecAndDrainHelper() {
        assertTrue(
            "BootLogic must have execAndDrain helper that drains both streams and calls waitFor",
            bootLogicContent.contains("fun execAndDrain") &&
            bootLogicContent.contains("inputStream") &&
            bootLogicContent.contains("errorStream") &&
            bootLogicContent.contains("waitFor()")
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

    @Test
    fun testHidesOemUnlockSupported() {
        assertTrue(
            "Must hide ro.oem_unlock_supported as 0 (moved from post-fs-data.sh to daemon)",
            bootLogicContent.contains("ro.oem_unlock_supported") &&
            bootLogicContent.contains("\"0\"")
        )
    }

    @Test
    fun testHidesVendorBootState() {
        assertTrue(
            "Must hide vendor.boot.vbmeta.device_state as locked (moved from post-fs-data.sh to daemon)",
            bootLogicContent.contains("vendor.boot.vbmeta.device_state") &&
            bootLogicContent.contains("locked")
        )
    }

    @Test
    fun testHidesVendorVerifiedBoot() {
        assertTrue(
            "Must hide vendor.boot.verifiedbootstate as green",
            bootLogicContent.contains("vendor.boot.verifiedbootstate") &&
            bootLogicContent.contains("green")
        )
    }

    @Test
    fun testHidesSecurebootLockstate() {
        assertTrue(
            "Must hide ro.secureboot.lockstate as locked",
            bootLogicContent.contains("ro.secureboot.lockstate") &&
            bootLogicContent.contains("locked")
        )
    }

    @Test
    fun testHidesRealmeBootstate() {
        assertTrue(
            "Must hide Realme-specific ro.boot.realmebootstate as green",
            bootLogicContent.contains("ro.boot.realmebootstate") &&
            bootLogicContent.contains("green")
        )
    }

    @Test
    fun testHidesRealmeLockstate() {
        assertTrue(
            "Must hide Realme-specific ro.boot.realme.lockstate as 1",
            bootLogicContent.contains("ro.boot.realme.lockstate") &&
            bootLogicContent.contains("\"1\"")
        )
    }

    @Test
    fun testApplyPropertyHidingCalledOnRun() {
        assertTrue(
            "BootLogic.run() must call applyPropertyHiding to set props from daemon startup",
            bootLogicContent.contains("applyPropertyHiding()")
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
    // Magisk 32-bit cleanup REMOVED
    // ================================
    // checkRemoveMagisk32 was removed in V2.2.6 because:
    //   - Deleting other modules' binaries risks bootloops
    //   - The module must not interfere with Magisk internals
    //   - Arch-specific binaries are managed by the root manager

    @Test
    fun testNoMagisk32Removal() {
        assertFalse(
            "BootLogic must NOT contain checkRemoveMagisk32 — deleting other modules' binaries risks bootloops",
            bootLogicContent.contains("checkRemoveMagisk32")
        )
    }

    @Test
    fun testNoMagisk32FlagFile() {
        assertFalse(
            "BootLogic must NOT reference FILE_REMOVE_MAGISK32 — module must not interfere with Magisk internals",
            bootLogicContent.contains("FILE_REMOVE_MAGISK32")
        )
    }

    @Test
    fun testNoDebugRamdiskPath() {
        assertFalse(
            "BootLogic must NOT contain /debug_ramdisk/magisk32 — must not delete root manager binaries",
            bootLogicContent.contains("/debug_ramdisk/magisk32")
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
