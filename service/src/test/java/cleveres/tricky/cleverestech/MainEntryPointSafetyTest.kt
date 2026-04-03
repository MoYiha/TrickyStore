package cleveres.tricky.cleverestech

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate Main.kt initialization order and safety.
 *
 * Validates that the service entry point correctly initializes all
 * subsystems, handles keystore failures gracefully, and maintains
 * proper loop structure.
 */
class MainEntryPointSafetyTest {

    private lateinit var mainContent: String

    @Before
    fun setup() {
        mainContent = serviceMainFile("Main.kt").readText()
    }

    // ================================
    // Initialization order
    // ================================

    @Test
    fun testRunsVerificationFirst() {
        val verifyLine = mainContent.lines().indexOfFirst { it.contains("Verification.check()") }
        val configLine = mainContent.lines().indexOfFirst { it.contains("Config.initialize()") }
        assertTrue("Verification.check() must exist", verifyLine >= 0)
        assertTrue("Config.initialize() must exist", configLine >= 0)
        assertTrue(
            "Verification must run BEFORE Config.initialize() to detect tampered files early",
            verifyLine < configLine
        )
    }

    @Test
    fun testStartsKeyboxAutoCleaner() {
        assertTrue(
            "Must start KeyboxAutoCleaner before entering main loop",
            mainContent.contains("KeyboxAutoCleaner.start()")
        )
    }

    @Test
    fun testStartsKeyboxFetcher() {
        assertTrue(
            "Must schedule KeyboxFetcher for remote key fetching",
            mainContent.contains("KeyboxFetcher.schedule()")
        )
    }

    @Test
    fun testStartsWebServer() {
        assertTrue(
            "Must start WebServer for configuration management",
            mainContent.contains("WebServer(") && mainContent.contains("server.startAsync()")
        )
    }

    @Test
    fun testInitializesRkpProxy() {
        assertTrue(
            "Must initialize LocalRkpProxy MAC key",
            mainContent.contains("LocalRkpProxy.getMacKey()")
        )
    }

    // ================================
    // Keystore loop
    // ================================

    @Test
    fun testKeystoreRetryLoop() {
        assertTrue(
            "Must retry keystore interceptor registration in a loop",
            mainContent.contains("KeystoreInterceptor.tryRunKeystoreInterceptor()")
        )
    }

    @Test
    fun testKeystoreSleepOnFailure() {
        assertTrue(
            "Must sleep before retrying keystore to avoid busy-wait",
            mainContent.contains("delay(1000)")
        )
    }

    @Test
    fun testConfigInitAfterKeystoreSuccess() {
        val lines = mainContent.lines()
        val ksSuccessLine = lines.indexOfFirst { it.contains("!ksSuccess") }
        val configInitLine = lines.indexOfFirst { it.contains("Config.initialize()") }
        assertTrue("ksSuccess check must exist", ksSuccessLine >= 0)
        assertTrue("Config.initialize() must exist", configInitLine >= 0)
        assertTrue(
            "Config.initialize() must come AFTER keystore success check",
            configInitLine > ksSuccessLine
        )
    }

    // ================================
    // Telephony interceptor handling
    // ================================

    @Test
    fun testTelephonyInterceptorRegistered() {
        assertTrue(
            "Must register TelephonyInterceptor",
            mainContent.contains("TelephonyInterceptor.tryRunTelephonyInterceptor()")
        )
    }

    @Test
    fun testTelephonyRetryInInnerLoop() {
        // Telephony should be retried in the inner loop
        val content = mainContent
        val innerLoopStart = content.indexOf("while (true)", content.indexOf("Config.initialize()"))
        assertTrue("Inner loop must exist after Config.initialize()", innerLoopStart > 0)
        val innerLoopSection = content.substring(innerLoopStart)
        assertTrue(
            "Telephony interceptor must be retried in the inner maintenance loop",
            innerLoopSection.contains("TelephonyInterceptor.tryRunTelephonyInterceptor()")
        )
    }

    // ================================
    // DRM interceptor handling
    // ================================

    @Test
    fun testDrmInterceptorInMainLoop() {
        assertTrue(
            "Must register DrmInterceptor in maintenance loop",
            mainContent.contains("DrmInterceptor.tryRunDrmInterceptor()")
        )
    }

    @Test
    fun testDrmRegistrationGuard() {
        assertTrue(
            "DRM interceptor should only be attempted if not already registered",
            mainContent.contains("drmRegistered")
        )
    }

    // ================================
    // Security
    // ================================

    @Test
    fun testWebServerPortFile() {
        assertTrue(
            "Web server port and token must be written to port file",
            mainContent.contains("web_port") && mainContent.contains("SecureFile.writeText")
        )
    }

    @Test
    fun testConfigDirPermissions() {
        assertTrue(
            "Config directory must have restricted permissions (0700 = 448)",
            mainContent.contains("448") || mainContent.contains("0700")
        )
    }

    @Test
    fun testRkpKeyPermissions() {
        assertTrue(
            "RKP key file must have restricted permissions (0600 = 384)",
            mainContent.contains("384") || mainContent.contains("0600")
        )
    }

    // ================================
    // RKP maintenance
    // ================================

    @Test
    fun testRkpRotationInMainLoop() {
        assertTrue(
            "Must call LocalRkpProxy.checkAndRotate() in maintenance loop",
            mainContent.contains("LocalRkpProxy.checkAndRotate()")
        )
    }

    @Test
    fun testMaintenanceLoopSleep() {
        assertTrue(
            "Maintenance loop must sleep (10s) to avoid CPU waste",
            mainContent.contains("delay(10000)")
        )
    }

    // ================================
    // CancellationException handling
    // ================================

    @Test
    fun testKeystoreSleepHandlesInterrupt() {
        assertTrue(
            "delay in keystore retry loop must catch CancellationException",
            mainContent.contains("CancellationException")
        )
    }

    @Test
    fun testInterruptSetsInterruptFlag() {
        assertTrue(
            "Must re-set interrupt flag via Thread.currentThread().interrupt() after catching CancellationException",
            mainContent.contains("Thread.currentThread().interrupt()")
        )
    }
}
