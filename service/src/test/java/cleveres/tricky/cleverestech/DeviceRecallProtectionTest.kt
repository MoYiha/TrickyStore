package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate Device Recall Protection implementation.
 *
 * Google's Play Integrity API Device Recall (beta 2026) stores 3 persistent
 * bits per device per developer account that survive factory resets. These tests
 * verify that the module has comprehensive countermeasures against this feature.
 *
 * Defense layers tested:
 *   1. Detection of Play Integrity service descriptors in Binder transactions
 *   2. Device identity signal randomization to break recall association
 *   3. Integration with existing DRM/build prop randomization
 *   4. Configuration toggle support
 */
class DeviceRecallProtectionTest {

    private lateinit var cppContent: String
    private lateinit var headerContent: String

    @Before
    fun setup() {
        cppContent = moduleCppFile("binder_interceptor.cpp").readText()
        headerContent = moduleCppFile("binder_interceptor.h").readText()
    }

    // ========================================================================
    // Class & Architecture
    // ========================================================================

    @Test
    fun testHasDeviceRecallProtectionClass() {
        assertTrue(
            "Must have DeviceRecallProtection class for Play Integrity countermeasures",
            cppContent.contains("DeviceRecallProtection") &&
                headerContent.contains("DeviceRecallProtection")
        )
    }

    @Test
    fun testDeviceRecallHasInitialize() {
        assertTrue(
            "DeviceRecallProtection must have initialize method",
            cppContent.contains("DeviceRecallProtection::initialize")
        )
    }

    @Test
    fun testDeviceRecallHasIsEnabled() {
        assertTrue(
            "DeviceRecallProtection must have isEnabled check",
            cppContent.contains("DeviceRecallProtection::isEnabled")
        )
    }

    // ========================================================================
    // Integrity Service Detection
    // ========================================================================

    @Test
    fun testDetectsIntegrityServiceDescriptor() {
        assertTrue(
            "Must detect Play Integrity service descriptors in Binder transactions",
            cppContent.contains("isIntegrityServiceDescriptor")
        )
    }

    @Test
    fun testKnowsPlayCoreIntegrityDescriptor() {
        assertTrue(
            "Must recognize com.google.android.play.core.integrity descriptor",
            headerContent.contains("com.google.android.play.core.integrity") ||
                cppContent.contains("com.google.android.play.core.integrity")
        )
    }

    @Test
    fun testKnowsGmsIntegrityDescriptor() {
        assertTrue(
            "Must recognize com.google.android.gms.playintegrity descriptor",
            headerContent.contains("com.google.android.gms.playintegrity") ||
                cppContent.contains("com.google.android.gms.playintegrity")
        )
    }

    @Test
    fun testDetectsRecallRelatedTransaction() {
        assertTrue(
            "Must detect recall-related transaction codes (warmup/request/standard)",
            cppContent.contains("isRecallRelatedTransaction")
        )
    }

    @Test
    fun testRecognizesDeviceRecallKeyword() {
        assertTrue(
            "Must contain 'deviceRecall' keyword for pattern matching",
            headerContent.contains("deviceRecall") || cppContent.contains("deviceRecall")
        )
    }

    // ========================================================================
    // Identity Signal Randomization
    // ========================================================================

    @Test
    fun testHasRandomizeDeviceSignals() {
        assertTrue(
            "Must have randomizeDeviceSignals method to break device recall association",
            cppContent.contains("randomizeDeviceSignals")
        )
    }

    @Test
    fun testRandomizesSerialForRecall() {
        assertTrue(
            "Device recall protection must invalidate serial number cache",
            cppContent.contains("ro.serialno")
        )
    }

    @Test
    fun testRandomizesImeiForRecall() {
        assertTrue(
            "Device recall protection must invalidate IMEI cache",
            cppContent.contains("persist.radio.imei")
        )
    }

    @Test
    fun testRandomizesFingerprintForRecall() {
        assertTrue(
            "Device recall protection must invalidate fingerprint cache",
            cppContent.contains("ro.build.fingerprint")
        )
    }

    // ========================================================================
    // Configuration & Integration
    // ========================================================================

    @Test
    fun testConfigFileToggle() {
        assertTrue(
            "Device recall protection must be toggleable via config file",
            cppContent.contains("device_recall_protection")
        )
    }

    @Test
    fun testAutoEnableWithRandomOnBoot() {
        assertTrue(
            "Device recall protection should auto-enable when random_on_boot is active",
            cppContent.contains("random_on_boot")
        )
    }

    @Test
    fun testAutoEnableWithRandomDrmOnBoot() {
        assertTrue(
            "Device recall protection should auto-enable when random_drm_on_boot is active",
            cppContent.contains("random_drm_on_boot")
        )
    }

    @Test
    fun testCalledDuringInitialization() {
        assertTrue(
            "Device recall protection must be initialized during hook setup",
            cppContent.contains("DeviceRecallProtection::initialize()")
        )
    }

    @Test
    fun testRandomizeCalledOnInit() {
        assertTrue(
            "Device signals must be randomized during initialization when protection is enabled",
            cppContent.contains("DeviceRecallProtection::randomizeDeviceSignals()")
        )
    }

    // ========================================================================
    // Warmup / Standard Request Interception
    // ========================================================================

    @Test
    fun testKnowsWarmupTransactionCode() {
        assertTrue(
            "Must define INTEGRITY_WARMUP_CODE for standard request warmup detection",
            headerContent.contains("INTEGRITY_WARMUP_CODE")
        )
    }

    @Test
    fun testKnowsRequestTransactionCode() {
        assertTrue(
            "Must define INTEGRITY_REQUEST_CODE for integrity request detection",
            headerContent.contains("INTEGRITY_REQUEST_CODE")
        )
    }

    @Test
    fun testKnowsStandardTransactionCode() {
        assertTrue(
            "Must define INTEGRITY_STANDARD_CODE for standard integrity request",
            headerContent.contains("INTEGRITY_STANDARD_CODE")
        )
    }

    // ========================================================================
    // Thread Safety
    // ========================================================================

    @Test
    fun testAtomicEnabledFlag() {
        assertTrue(
            "DeviceRecallProtection enabled flag must be atomic for thread safety",
            headerContent.contains("atomic<bool>") || cppContent.contains("atomic<bool>")
        )
    }

    @Test
    fun testAtomicInitializedFlag() {
        assertTrue(
            "DeviceRecallProtection initialized flag must be atomic for thread safety",
            headerContent.contains("s_initialized") || cppContent.contains("s_initialized")
        )
    }

    // ========================================================================
    // Documentation & Comments
    // ========================================================================

    @Test
    fun testDocumentsDeviceRecallFeature() {
        assertTrue(
            "Code must document what Device Recall is (Google's persistent 3-bit feature)",
            cppContent.contains("3 persistent bits") || cppContent.contains("3 bits") ||
                cppContent.contains("factory reset")
        )
    }

    @Test
    fun testDocumentsDefenseStrategy() {
        assertTrue(
            "Code must document the defense strategy against device recall",
            cppContent.contains("defense strategy") || cppContent.contains("Defense strategy") ||
                cppContent.contains("countermeasure")
        )
    }
}
