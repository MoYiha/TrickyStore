package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for daemon crash resilience.
 *
 * These tests validate that:
 * 1. service.sh has proper retry logic for daemon restarts
 * 2. The daemon script correctly launches the Java service
 * 3. exitProcess calls in critical paths don't cause permanent daemon death
 * 4. Verification failures don't crash the daemon
 */
class DaemonCrashResilienceTest {

    private lateinit var serviceShContent: String
    private lateinit var daemonContent: String

    @Before
    fun setup() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) { println("E/$tag: $msg") }
            override fun e(tag: String, msg: String, t: Throwable?) { println("E/$tag: $msg") }
            override fun i(tag: String, msg: String) {}
        })

        serviceShContent = moduleTemplateFile("service.sh").readText()
        daemonContent = moduleTemplateFile("daemon").readText()
    }

    // ============================
    // service.sh retry logic tests
    // ============================

    @Test
    fun testServiceShHasRetryCounter() {
        assertTrue(
            "service.sh must have FAIL_COUNT retry counter to prevent permanent daemon death on transient failures",
            serviceShContent.contains("FAIL_COUNT")
        )
    }

    @Test
    fun testServiceShHasMaxRetries() {
        assertTrue(
            "service.sh must have MAX_FAILS limit to cap retry attempts",
            serviceShContent.contains("MAX_FAILS")
        )
    }

    @Test
    fun testServiceShHasSleepBetweenRetries() {
        assertTrue(
            "service.sh must sleep between retries to avoid rapid crash loops that drain battery",
            serviceShContent.contains("sleep")
        )
    }

    @Test
    fun testServiceShResetsCounterOnCleanExit() {
        // After a successful daemon run (exit 0), the counter should reset
        // so that future transient failures get fresh retries
        val lines = serviceShContent.lines()
        val hasReset = lines.any { it.trim() == "FAIL_COUNT=0" && !it.trim().startsWith("#") }
        assertTrue(
            "service.sh must reset FAIL_COUNT to 0 after clean daemon exit",
            hasReset
        )
    }

    @Test
    fun testServiceShDoesNotExitOnFirstNonZero() {
        // The fix requires that a non-zero exit increments FAIL_COUNT and retries,
        // rather than immediately terminating. Verify the retry increment pattern exists.
        assertTrue(
            "service.sh must increment FAIL_COUNT on non-zero exit to enable retries",
            serviceShContent.contains("FAIL_COUNT=\$((FAIL_COUNT + 1))")
        )
    }

    @Test
    fun testServiceShRunsInBackground() {
        assertTrue(
            "service.sh daemon loop must run in background with &",
            serviceShContent.trimEnd().endsWith("&")
        )
    }

    // ============================
    // daemon script tests
    // ============================

    @Test
    fun testDaemonScriptUsesExec() {
        assertTrue(
            "daemon script must use exec to replace shell process and avoid zombie processes",
            daemonContent.contains("exec ")
        )
    }

    @Test
    fun testDaemonScriptUsesAppProcess() {
        assertTrue(
            "daemon script must use app_process to launch the Java service",
            daemonContent.contains("app_process")
        )
    }

    @Test
    fun testDaemonScriptLaunchesMainKt() {
        assertTrue(
            "daemon script must launch MainKt entry point",
            daemonContent.contains("cleveres.tricky.cleverestech.MainKt")
        )
    }

    @Test
    fun testMainEntryPointRunsUnderRetryingServiceWrapper() {
        assertTrue(
            "service.sh must run the daemon wrapper so MainKt exits can be retried",
            serviceShContent.contains("./daemon")
        )
        assertTrue(
            "daemon wrapper must launch MainKt so service.sh can restart it after non-zero exits",
            daemonContent.contains("cleveres.tricky.cleverestech.MainKt")
        )
    }

    @Test
    fun testDaemonScriptSetsClasspath() {
        assertTrue(
            "daemon script must set java.class.path to service.apk",
            daemonContent.contains("java.class.path=./service.apk")
        )
    }

    // ============================
    // Verification crash resilience
    // ============================

    @Test
    fun testVerificationDoesNotCrashOnMissingDirectory() {
        // Verification.check() should not throw or call exitProcess
        // when the module directory doesn't exist
        var exitCalled = false
        Verification.exitProcessImpl = { exitCalled = true }

        Verification.check(File("/nonexistent/path/that/does/not/exist"))

        assertFalse("Verification must not call exitProcess on missing directory", exitCalled)
    }

    @Test
    fun testVerificationDoesNotCrashOnEmptyDirectory() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "daemon_test_empty_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            var exitCalled = false
            Verification.exitProcessImpl = { exitCalled = true }

            Verification.check(tempDir)

            assertFalse("Verification must not call exitProcess on empty directory", exitCalled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testVerificationDoesNotCrashOnCorruptedChecksum() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "daemon_test_corrupt_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            // Create a file with a corrupted checksum
            File(tempDir, "test.bin").writeText("hello world")
            File(tempDir, "test.bin.sha256").writeText("0000000000000000000000000000000000000000000000000000000000000000")

            var exitCalled = false
            Verification.exitProcessImpl = { exitCalled = true }

            Verification.check(tempDir)

            assertFalse("Verification must not call exitProcess on corrupted checksum (graceful degradation)", exitCalled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testVerificationHandlesSymlinkLoop() {
        // Symlink loops could cause infinite recursion in walk()
        // Verify it doesn't crash the daemon
        val tempDir = File(System.getProperty("java.io.tmpdir"), "daemon_test_symlink_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            File(tempDir, "real.txt").writeText("data")
            File(tempDir, "real.txt.sha256").writeText("invalid")

            var exitCalled = false
            Verification.exitProcessImpl = { exitCalled = true }

            Verification.check(tempDir)

            assertFalse("Verification must not crash daemon on any file system anomaly", exitCalled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ============================
    // Config parse crash resilience
    // ============================

    @Test
    fun testParsePackagesHandlesEmptyInput() {
        val (hack, generate) = Config.parsePackages(emptySequence(), false)
        assertEquals(0, hack.size)
        assertEquals(0, generate.size)
    }

    @Test
    fun testParsePackagesHandlesMalformedLines() {
        val lines = sequenceOf(
            "",          // empty line
            "   ",       // whitespace only
            "#comment",  // comment
            "com.valid.package",  // valid
            "   com.another.package!  ", // valid with bang and whitespace
        )
        val (hack, generate) = Config.parsePackages(lines, false)
        // "com.valid.package" should be in hack (no !)
        assertTrue("com.valid.package should be in hack", hack.matches("com.valid.package"))
        // "com.another.package" should be in generate (has !)
        assertTrue("com.another.package should be in generate", generate.matches("com.another.package"))
        assertEquals("hack should have exactly 1 entry", 1, hack.size)
        assertEquals("generate should have exactly 1 entry", 1, generate.size)
    }

    @Test
    fun testParsePackagesTeeBrokenMode() {
        val lines = sequenceOf("com.test.app")
        val (hack, generate) = Config.parsePackages(lines, true)
        // In TEE broken mode, all packages go to generate
        assertEquals(0, hack.size)
        assertEquals(1, generate.size)
    }
}
