package cleveres.tricky.cleverestech

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate Verification.kt code correctness.
 *
 * Validates checksum calculation, ignored files, and graceful
 * degradation on failures.
 */
class VerificationSafetyTest {

    private lateinit var verificationContent: String

    @Before
    fun setup() {
        verificationContent = serviceMainFile("Verification.kt").readText()
    }

    // ================================
    // Checksum algorithm
    // ================================

    @Test
    fun testUsesSha256() {
        assertTrue(
            "Must use SHA-256 for file integrity verification",
            verificationContent.contains("SHA-256")
        )
    }

    @Test
    fun testUsesHexFormat() {
        assertTrue(
            "Must output checksums as hex strings for comparison with .sha256 files",
            verificationContent.contains("toHexString") || verificationContent.contains("HexFormat")
        )
    }

    // ================================
    // Ignored files
    // ================================

    @Test
    fun testIgnoresDisableFile() {
        assertTrue(
            "Must ignore 'disable' file (used by Magisk to disable module)",
            verificationContent.contains("\"disable\"")
        )
    }

    @Test
    fun testIgnoresRemoveFile() {
        assertTrue(
            "Must ignore 'remove' file (used by Magisk to remove module)",
            verificationContent.contains("\"remove\"")
        )
    }

    @Test
    fun testIgnoresUpdateFile() {
        assertTrue(
            "Must ignore 'update' file",
            verificationContent.contains("\"update\"")
        )
    }

    @Test
    fun testIgnoresSepolicyRule() {
        assertTrue(
            "Must ignore 'sepolicy.rule' (modified during Magisk install)",
            verificationContent.contains("\"sepolicy.rule\"")
        )
    }

    @Test
    fun testIgnoresSystemProp() {
        assertTrue(
            "Must ignore 'system.prop'",
            verificationContent.contains("\"system.prop\"")
        )
    }

    // ================================
    // Graceful degradation
    // ================================

    @Test
    fun testDoesNotExitOnMissingDirectory() {
        assertTrue(
            "Must return early (not crash) when module directory doesn't exist",
            verificationContent.contains("!root.exists()") && verificationContent.contains("return")
        )
    }

    @Test
    fun testDoesNotDisableModule() {
        // The fail() method should NOT create disable file or exit
        // (it's currently commented out — verify it stays that way)
        assertTrue(
            "fail() must log but NOT disable module (graceful degradation)",
            verificationContent.contains("Verification failed (ignored)")
        )
    }

    @Test
    fun testExitProcessImplIsTestable() {
        assertTrue(
            "exitProcessImpl must be a var for test injection",
            verificationContent.contains("var exitProcessImpl")
        )
    }

    // ================================
    // Checksum map building
    // ================================

    @Test
    fun testBuildsChecksumMap() {
        assertTrue(
            "Must build a map of expected checksums from .sha256 files",
            verificationContent.contains(".sha256") && verificationContent.contains("associate")
        )
    }

    @Test
    fun testComparesIgnoringCase() {
        assertTrue(
            "Checksum comparison must be case-insensitive (hex can be upper/lower)",
            verificationContent.contains("ignoreCase = true")
        )
    }
}
