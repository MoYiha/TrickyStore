package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate DRM interceptor code correctness and safety.
 *
 * Validates retry logic, process stream handling, transaction codes,
 * death recipient handling, and SecureRandom usage.
 */
class DrmInterceptorSafetyTest {

    private lateinit var drmContent: String

    @Before
    fun setup() {
        drmContent = serviceMainFile("DrmInterceptor.kt").readText()
    }

    // ================================
    // Retry logic correctness
    // ================================

    @Test
    fun testRetryCounterIncrementedOnServiceNotFound() {
        // Bug fix validation: triedCount must be incremented even when findDrmService() returns null
        // to prevent infinite retry loops
        val lines = drmContent.lines()
        val serviceNotFoundIdx = lines.indexOfFirst { it.contains("DRM service not found") }
        assertTrue("Must have 'DRM service not found' log", serviceNotFoundIdx >= 0)

        // Search within the tryRunDrmInterceptor function body for the increment near the log
        val functionStart = lines.indexOfFirst { it.contains("fun tryRunDrmInterceptor") }
        assertTrue("tryRunDrmInterceptor function must exist", functionStart >= 0)
        val functionBody = lines.subList(functionStart, lines.size).joinToString("\n")

        // The increment must appear after "DRM service not found" and before the next return
        val afterNotFound = drmContent.substring(drmContent.indexOf("DRM service not found"))
        val nextReturn = afterNotFound.indexOf("return")
        assertTrue("Must have return after service not found", nextReturn > 0)
        val betweenLogAndReturn = afterNotFound.substring(0, nextReturn)
        assertTrue(
            "triedCount must be incremented between 'DRM service not found' and 'return' to prevent infinite retry loop",
            betweenLogAndReturn.contains("triedCount += 1") || betweenLogAndReturn.contains("triedCount++")
        )
    }

    @Test
    fun testRetryLimitExists() {
        assertTrue(
            "DRM interceptor must have retry limit (triedCount >= 3)",
            drmContent.contains("triedCount >= 3")
        )
    }

    @Test
    fun testDeathRecipientRegistered() {
        assertTrue(
            "DRM interceptor must register death recipient to detect service crashes",
            drmContent.contains("linkToDeath")
        )
    }

    @Test
    fun testDeathRecipientResetsState() {
        assertTrue(
            "Death recipient must reset injected state",
            drmContent.contains("injected = false")
        )
        assertTrue(
            "Death recipient must reset triedCount",
            drmContent.contains("triedCount = 0")
        )
    }

    // ================================
    // DRM transaction codes
    // ================================

    @Test
    fun testInterceptsGetPropertyString() {
        assertTrue(
            "Must intercept TRANSACTION_GET_PROPERTY_STRING (17) for Widevine level spoofing",
            drmContent.contains("TRANSACTION_GET_PROPERTY_STRING") && drmContent.contains("= 17")
        )
    }

    @Test
    fun testInterceptsGetPropertyByteArray() {
        assertTrue(
            "Must intercept TRANSACTION_GET_PROPERTY_BYTE_ARRAY (18) for DRM ID spoofing",
            drmContent.contains("TRANSACTION_GET_PROPERTY_BYTE_ARRAY") && drmContent.contains("= 18")
        )
    }

    @Test
    fun testUsesSecureRandomForDrmId() {
        assertTrue(
            "DRM ID spoofing must use SecureRandom (not Random)",
            drmContent.contains("SecureRandom")
        )
    }

    // ================================
    // Process injection safety
    // ================================

    @Test
    fun testDrainsProcessStreams() {
        assertTrue(
            "Process injection must drain stdout/stderr to prevent FD exhaustion",
            drmContent.contains("inputStream.readBytes()") && drmContent.contains("errorStream.readBytes()")
        )
    }

    @Test
    fun testSearchesMultipleDrmServiceNames() {
        assertTrue(
            "Must search for multiple DRM service names for device compatibility",
            drmContent.contains("drm.IDrmFactory/widevine") &&
            drmContent.contains("android.hardware.drm.IDrmFactory/widevine")
        )
    }

    @Test
    fun testSearchesMultipleDrmProcessNames() {
        assertTrue(
            "Must search multiple DRM process names (AIDL + HIDL variants)",
            drmContent.contains("android.hardware.drm-service.widevine") &&
            drmContent.contains("android.hardware.drm@1.4-service.widevine")
        )
    }

    // ================================
    // Widevine spoofing correctness
    // ================================

    @Test
    fun testSpoofWidevineLevel() {
        assertTrue(
            "Must spoof Widevine security level (L3->L1)",
            drmContent.contains("ro.com.google.widevine.level") &&
            (drmContent.contains("L3") || drmContent.contains("L2"))
        )
    }

    @Test
    fun testRandomDrmOnBootFlag() {
        assertTrue(
            "Must check random_drm_on_boot flag file for DRM ID randomization",
            drmContent.contains("random_drm_on_boot")
        )
    }

    @Test
    fun testReadsRequestedPropertyNameFromDataParcel() {
        assertTrue(
            "DRM interceptor must inspect the request Parcel so it only spoofs tracked DRM properties",
            drmContent.contains("readTrackedPropertyName(data)") &&
            drmContent.contains("data.readString()")
        )
    }

    @Test
    fun testTargetsSpecificDrmProperties() {
        assertTrue(
            "DRM interceptor must only target securityLevel and deviceUniqueId properties",
            drmContent.contains("SECURITY_LEVEL_PROPERTY") &&
            drmContent.contains("DEVICE_UNIQUE_ID_PROPERTY")
        )
    }

    @Test
    fun testDrmIdIs32Bytes() {
        assertTrue(
            "Spoofed DRM device ID must be 32 bytes",
            drmContent.contains("ByteArray(32)")
        )
    }
}
