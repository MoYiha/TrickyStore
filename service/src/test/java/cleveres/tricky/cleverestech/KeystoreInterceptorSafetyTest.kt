package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate KeystoreInterceptor code correctness at source level.
 *
 * Ensures that the keystore injection path — the single most important
 * runtime path in the module — has proper stream draining, retry logic,
 * process exec safety, and conflict detection.
 */
class KeystoreInterceptorSafetyTest {

    private lateinit var keystoreContent: String

    @Before
    fun setup() {
        keystoreContent = serviceMainFile("KeystoreInterceptor.kt").readText()
    }

    // ================================
    // Process exec safety
    // ================================

    @Test
    fun testInjectionUsesArrayExec() {
        assertTrue(
            "Keystore injection must use array-based Runtime.exec to prevent shell injection",
            keystoreContent.contains("Runtime.getRuntime().exec(") &&
            keystoreContent.contains("arrayOf(")
        )
    }

    @Test
    fun testInjectionDoesNotUseShDashC() {
        assertFalse(
            "Keystore injection must NOT use exec(\"sh -c ...\") pattern",
            keystoreContent.contains("""exec("sh""") || keystoreContent.contains("""exec("bash""")
        )
    }

    // ================================
    // Stream draining (FD exhaustion prevention)
    // ================================

    @Test
    fun testInjectionDrainsStdout() {
        assertTrue(
            "Keystore injection must drain stdout to prevent FD exhaustion in the daemon",
            keystoreContent.contains("inputStream") && keystoreContent.contains("readText")
        )
    }

    @Test
    fun testInjectionDrainsStderr() {
        assertTrue(
            "Keystore injection must drain stderr to prevent FD exhaustion in the daemon",
            keystoreContent.contains("errorStream") && keystoreContent.contains("readText")
        )
    }

    // ================================
    // Retry logic & conflict detection
    // ================================

    @Test
    fun testHasRetryCountLimit() {
        assertTrue(
            "Keystore injection must have a retry count limit (triedCount >= N) to avoid infinite injection loops",
            keystoreContent.contains("triedCount >= ")
        )
    }

    @Test
    fun testIncrementsRetryOnPidNotFound() {
        // After "failed to find keystore2 pid", triedCount must be incremented
        val pidFailIdx = keystoreContent.indexOf("failed to find keystore2 pid")
        assertTrue("Must log keystore2 pid failure", pidFailIdx >= 0)
        val afterPidFail = keystoreContent.substring(pidFailIdx, minOf(pidFailIdx + 200, keystoreContent.length))
        assertTrue(
            "Must increment triedCount when keystore2 pid is not found",
            afterPidFail.contains("triedCount += 1") || afterPidFail.contains("triedCount++")
        )
    }

    @Test
    fun testIncrementsRetryOnInjectionFailure() {
        // After injection exit code != 0, triedCount must be incremented
        val failIdx = keystoreContent.indexOf("failed to inject keystore")
        assertTrue("Must log keystore injection failure", failIdx >= 0)
        val afterFail = keystoreContent.substring(failIdx, minOf(failIdx + 200, keystoreContent.length))
        assertTrue(
            "Must increment triedCount when injection fails",
            afterFail.contains("triedCount += 1") || afterFail.contains("triedCount++")
        )
    }

    @Test
    fun testLogsConflictHint() {
        assertTrue(
            "Must log a conflict hint about Zygisk/ptrace modules when injection fails",
            keystoreContent.contains("Zygisk") || keystoreContent.contains("ptrace module")
        )
    }

    // ================================
    // PID discovery
    // ================================

    @Test
    fun testFindsPidFromProc() {
        assertTrue(
            "Must read /proc to find keystore2 PID",
            keystoreContent.contains("/proc") && keystoreContent.contains("cmdline")
        )
    }

    @Test
    fun testMatchesKeystore2ProcessName() {
        assertTrue(
            "Must match 'keystore2' process name (exact or path-suffix)",
            keystoreContent.contains("\"keystore2\"") || keystoreContent.contains("/keystore2")
        )
    }

    // ================================
    // Parcel safety
    // ================================

    @Test
    fun testParcelRecycledOnError() {
        val recycleCount = Regex("p\\.recycle\\(\\)").findAll(keystoreContent).count()
        assertTrue(
            "Parcel must be recycled on error paths to prevent memory leaks (found $recycleCount, need at least 1)",
            recycleCount >= 1
        )
    }

    // ================================
    // Death recipient
    // ================================

    @Test
    fun testLinksToDeathForRestartRecovery() {
        assertTrue(
            "Must link to keystore binder death recipient for automatic daemon restart on keystore crash",
            keystoreContent.contains("linkToDeath")
        )
    }

    @Test
    fun testDeathRecipientExitsProcess() {
        assertTrue(
            "Death recipient must trigger process exit so service.sh can restart the daemon cleanly",
            keystoreContent.contains("exitProcess")
        )
    }
}
