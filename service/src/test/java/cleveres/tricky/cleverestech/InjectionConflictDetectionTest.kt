package cleveres.tricky.cleverestech

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate injection conflict detection and verbose logging
 * for debugging keystore hooking failures in real-world scenarios.
 *
 * These tests scan source files for patterns critical to diagnosing
 * injection failures when running alongside other modules (e.g., Play
 * Integrity Fork, KernelSU).
 */
class InjectionConflictDetectionTest {

    private lateinit var injectMainContent: String
    private lateinit var keystoreInterceptorContent: String
    private lateinit var configContent: String

    @Before
    fun setup() {
        injectMainContent = moduleCppFile("inject/main.cpp").readText()
        keystoreInterceptorContent = serviceMainFile("KeystoreInterceptor.kt").readText()
        configContent = serviceMainFile("Config.kt").readText()
    }

    // ================================
    // C++ injection conflict detection
    // ================================

    @Test
    fun testPtraceAttachEpermConflictDetection() {
        assertTrue(
            "PTRACE_ATTACH must check for EPERM and log a conflict warning about other Zygisk/ptrace modules",
            injectMainContent.contains("EPERM") && injectMainContent.contains("another module")
        )
    }

    @Test
    fun testPtraceAttachLogsTargetPid() {
        assertTrue(
            "PTRACE_ATTACH failure must log the target pid for real-world debugging",
            injectMainContent.contains("PTRACE_ATTACH failed") && injectMainContent.contains("pid")
        )
    }

    @Test
    fun testInjectionEntryLogsTargetProcess() {
        assertTrue(
            "inject_library must log the target process information at entry",
            injectMainContent.contains("target process")
        )
    }

    // ================================
    // Kotlin KeystoreInterceptor logging
    // ================================

    @Test
    fun testKeystoreInterceptorLogsAttemptCount() {
        assertTrue(
            "KeystoreInterceptor must log the attempt count for retry tracking",
            keystoreInterceptorContent.contains("attempt=")
        )
    }

    @Test
    fun testKeystoreInterceptorLogsPid() {
        assertTrue(
            "KeystoreInterceptor must log the keystore2 pid when found",
            keystoreInterceptorContent.contains("pid=")
        )
    }

    @Test
    fun testKeystoreInterceptorLogsConflictHint() {
        assertTrue(
            "KeystoreInterceptor must hint at possible module conflicts when injection fails",
            keystoreInterceptorContent.contains("conflict")
        )
    }

    // ================================
    // Config verbose logging
    // ================================

    @Test
    fun testConfigLogsTargetTxtPath() {
        assertTrue(
            "Config must log target.txt file path when reading for debugging",
            configContent.contains("target.txt") && configContent.contains("absolutePath")
        )
    }

    @Test
    fun testConfigLogsKeyboxScan() {
        assertTrue(
            "Config must log keybox scanning activity for debugging",
            configContent.contains("updateKeyBoxes: starting keybox scan")
        )
    }

    @Test
    fun testConfigLogsRkpBypassState() {
        assertTrue(
            "Config must log RKP bypass file path and existence for debugging",
            configContent.contains("RKP bypass") && configContent.contains("file=")
        )
    }

    @Test
    fun testConfigLogsTotalKeyboxCount() {
        assertTrue(
            "Config must log total keybox count after loading for debugging",
            configContent.contains("keyboxes loaded and active")
        )
    }

    @Test
    fun testConfigLogsInitialization() {
        assertTrue(
            "Config.initialize must log at entry for debugging startup issues",
            configContent.contains("Config.initialize: starting")
        )
    }
}
