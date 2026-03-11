package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate shell scripts do NOT contain resetprop calls.
 *
 * All property hiding must happen inside the compiled daemon (BootLogic.kt)
 * to avoid detection by integrity frameworks that scan shell scripts for
 * resetprop usage.  The daemon's compiled bytecode is far harder to fingerprint
 * than plain-text shell commands.
 *
 * This test prevents regressions where a developer might re-add resetprop
 * to shell scripts for convenience.
 */
class DetectionAvoidanceTest {

    private lateinit var postFsDataContent: String
    private lateinit var serviceShContent: String
    private lateinit var bootLogicContent: String

    @Before
    fun setup() {
        postFsDataContent = moduleTemplateFile("post-fs-data.sh").readText()
        serviceShContent = moduleTemplateFile("service.sh").readText()
        bootLogicContent = serviceMainFile("BootLogic.kt").readText()
    }

    // ================================
    // Shell scripts must NOT use resetprop
    // ================================

    @Test
    fun testPostFsDataNoResetprop() {
        // Check that no line starts with a resetprop command (ignore comments)
        val hasResetpropCall = postFsDataContent.lines().any { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") && Regex("""(^|\s)resetprop\s""").containsMatchIn(trimmed)
        }
        assertFalse(
            "post-fs-data.sh must NOT contain executable resetprop commands — property hiding must be in daemon (BootLogic.kt) to avoid detection",
            hasResetpropCall
        )
    }

    @Test
    fun testServiceShNoResetprop() {
        assertFalse(
            "service.sh must NOT contain resetprop commands — property hiding must be in daemon (BootLogic.kt)",
            serviceShContent.contains("resetprop ")
        )
    }

    // ================================
    // Daemon must be the authority for property hiding
    // ================================

    @Test
    fun testBootLogicAppliesPropertyHiding() {
        assertTrue(
            "BootLogic.kt must call applyPropertyHiding to set boot/verified properties from daemon",
            bootLogicContent.contains("applyPropertyHiding")
        )
    }

    @Test
    fun testBootLogicHidesVerifiedBoot() {
        assertTrue(
            "BootLogic.kt must hide ro.boot.verifiedbootstate in applyPropertyHiding",
            bootLogicContent.contains("ro.boot.verifiedbootstate")
        )
    }

    @Test
    fun testBootLogicHidesOemUnlock() {
        assertTrue(
            "BootLogic.kt must hide ro.oem_unlock_supported in applyPropertyHiding",
            bootLogicContent.contains("ro.oem_unlock_supported")
        )
    }

    @Test
    fun testBootLogicHidesVendorBootState() {
        assertTrue(
            "BootLogic.kt must hide vendor.boot.vbmeta.device_state in applyPropertyHiding",
            bootLogicContent.contains("vendor.boot.vbmeta.device_state")
        )
    }

    @Test
    fun testBootLogicHidesRealmeProps() {
        assertTrue(
            "BootLogic.kt must hide Realme-specific boot props",
            bootLogicContent.contains("ro.boot.realmebootstate") &&
            bootLogicContent.contains("ro.boot.realme.lockstate")
        )
    }

    @Test
    fun testBootLogicHidesBootmode() {
        assertTrue(
            "BootLogic.kt must handle bootmode hiding (recovery -> unknown)",
            bootLogicContent.contains("hideBootMode")
        )
    }

    // ================================
    // SELinux auto-repair in service.sh
    // ================================

    @Test
    fun testServiceShAutoRepairsSELinuxContexts() {
        assertTrue(
            "service.sh must auto-repair SELinux contexts before each daemon launch for self-healing",
            serviceShContent.contains("chcon") && serviceShContent.contains("cleverestricky_exec")
        )
    }

    @Test
    fun testServiceShRepairsInjectBinary() {
        assertTrue(
            "service.sh must repair SELinux context of inject binary for self-healing injection",
            serviceShContent.contains("inject") && serviceShContent.contains("chcon")
        )
    }

    // ================================
    // service.sh retry safety
    // ================================

    @Test
    fun testServiceShHasMaxBackoffCycles() {
        assertTrue(
            "service.sh must have MAX_BACKOFF_CYCLES to prevent infinite retry-backoff loops",
            serviceShContent.contains("MAX_BACKOFF_CYCLES")
        )
    }

    @Test
    fun testServiceShBreaksAfterMaxBackoff() {
        assertTrue(
            "service.sh must break out of the loop after exhausting backoff cycles",
            serviceShContent.contains("break")
        )
    }

    // ================================
    // post-fs-data.sh glob safety
    // ================================

    @Test
    fun testPostFsDataUsesFind() {
        assertTrue(
            "post-fs-data.sh must use find (not glob) for chcon to avoid errors when no files match",
            postFsDataContent.contains("find") && postFsDataContent.contains("-exec")
        )
    }

    @Test
    fun testPostFsDataSuppressesChconErrors() {
        assertTrue(
            "post-fs-data.sh must suppress chcon errors (2>/dev/null) for robustness",
            postFsDataContent.contains("2>/dev/null")
        )
    }
}
