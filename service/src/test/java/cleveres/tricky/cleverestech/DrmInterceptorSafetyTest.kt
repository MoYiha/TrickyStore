package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for DRM interceptor code correctness and safety.
 *
 * Validates retry logic, process stream handling, transaction codes,
 * death recipient handling, SecureRandom usage, config caching,
 * Parcel lifecycle, thread safety, and Widevine service discovery.
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
        val afterNotFound = drmContent.substring(drmContent.indexOf("Service not found"))
        val nextReturn = afterNotFound.indexOf("return")
        assertTrue("Must have return after service not found", nextReturn > 0)
        val betweenLogAndReturn = afterNotFound.substring(0, nextReturn)
        assertTrue(
            "triedCount must be incremented between 'not found' and 'return' to prevent infinite retry loop",
            betweenLogAndReturn.contains("triedCount += 1") || betweenLogAndReturn.contains("triedCount++")
        )
    }

    @Test
    fun testRetryCounterIncrementedOnPidNotFound() {
        val afterPidNotFound = drmContent.substring(drmContent.indexOf("Cannot find DRM service PID"))
        val nextReturn = afterPidNotFound.indexOf("return")
        assertTrue("Must have return after PID not found", nextReturn > 0)
        val between = afterPidNotFound.substring(0, nextReturn)
        assertTrue(
            "triedCount must be incremented when PID not found",
            between.contains("triedCount += 1") || between.contains("triedCount++")
        )
    }

    @Test
    fun testRetryCounterIncrementedAfterInjection() {
        val afterInjection = drmContent.substring(drmContent.indexOf("Injection succeeded"))
        val nextReturn = afterInjection.indexOf("return false")
        assertTrue("Must have 'return false' after injection block", nextReturn > 0)
        val between = afterInjection.substring(0, nextReturn)
        assertTrue(
            "triedCount must be incremented after injection attempt block",
            between.contains("triedCount += 1") || between.contains("triedCount++")
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
    fun testRetryLimitLogsExhaustion() {
        assertTrue(
            "Must log when retry limit is exhausted",
            drmContent.contains("Exhausted") || drmContent.contains("giving up")
        )
    }

    // ================================
    // Death recipient handling
    // ================================

    @Test
    fun testDeathRecipientRegistered() {
        assertTrue(
            "DRM interceptor must register death recipient to detect service crashes",
            drmContent.contains("linkToDeath")
        )
    }

    @Test
    fun testDeathRecipientResetsInjectedState() {
        assertTrue(
            "Death recipient must reset injected state",
            drmContent.contains("injected = false")
        )
    }

    @Test
    fun testDeathRecipientResetsTriedCount() {
        assertTrue(
            "Death recipient must reset triedCount to allow re-registration",
            drmContent.contains("triedCount = 0")
        )
    }

    @Test
    fun testDeathRecipientResetsDrmBinder() {
        assertTrue(
            "Death recipient must null out drmBinder reference",
            drmContent.contains("drmBinder = null")
        )
    }

    @Test
    fun testDeathRecipientInvalidatesConfigCache() {
        assertTrue(
            "Death recipient must reset config cache time for fresh reload",
            drmContent.contains("cachedDrmConfigTime = 0") || drmContent.contains("cachedDrmConfigTime=0")
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

    // ================================
    // SecureRandom and thread safety
    // ================================

    @Test
    fun testUsesSecureRandomForDrmId() {
        assertTrue(
            "DRM ID spoofing must use SecureRandom (not kotlin.random.Random)",
            drmContent.contains("SecureRandom")
        )
        assertFalse(
            "Must NOT use insecure kotlin.random.Random",
            drmContent.contains("import kotlin.random.Random")
        )
    }

    @Test
    fun testUsesThreadLocalSecureRandom() {
        assertTrue(
            "Must use ThreadLocal<SecureRandom> to avoid contention and per-call allocation",
            drmContent.contains("ThreadLocal<SecureRandom>")
        )
    }

    @Test
    fun testVolatileStateFields() {
        assertTrue(
            "drmBinder must be @Volatile for cross-thread visibility",
            drmContent.contains("@Volatile") && drmContent.contains("var drmBinder")
        )
        assertTrue(
            "triedCount must be @Volatile for cross-thread visibility",
            drmContent.contains("@Volatile") && drmContent.contains("var triedCount")
        )
        assertTrue(
            "injected must be @Volatile for cross-thread visibility",
            drmContent.contains("@Volatile") && drmContent.contains("var injected")
        )
    }

    // ================================
    // Config caching (no I/O on hot path)
    // ================================

    @Test
    fun testConfigCacheExists() {
        assertTrue(
            "Must cache random_drm_on_boot config to avoid per-transaction file I/O",
            drmContent.contains("cachedRandomDrmOnBoot")
        )
    }

    @Test
    fun testConfigCacheHasTTL() {
        assertTrue(
            "Config cache must have a TTL to periodically refresh",
            drmContent.contains("CONFIG_CACHE_TTL_MS")
        )
    }

    @Test
    fun testNoDirectFileCheckInTransactionHandler() {
        val handleMethod = extractMethod(drmContent, "handleGetPropertyByteArray")
        assertFalse(
            "handleGetPropertyByteArray must use cached config, not File().exists() on every transaction",
            handleMethod.contains("File(") && handleMethod.contains(".exists()")
        )
    }

    // ================================
    // Parcel lifecycle
    // ================================

    @Test
    fun testParcelRecycledOnError() {
        assertTrue(
            "Parcel must be recycled on error to prevent native memory leaks",
            drmContent.contains("p.recycle()")
        )
    }

    @Test
    fun testExceptionReadBeforeStringParse() {
        val handleStr = extractMethod(drmContent, "handleGetPropertyString")
        val readExceptionIdx = handleStr.indexOf("readException")
        val readStringIdx = handleStr.indexOf("readString")
        assertTrue("readException() must be called", readExceptionIdx >= 0)
        assertTrue("readString() must be called", readStringIdx >= 0)
        assertTrue(
            "readException must come before readString in handleGetPropertyString",
            readExceptionIdx < readStringIdx
        )
    }

    @Test
    fun testNullReplyGuard() {
        assertTrue(
            "Must guard against null reply in onPostTransact",
            drmContent.contains("reply == null") && drmContent.contains("return Skip")
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
    fun testUsesArrayBasedExec() {
        assertTrue(
            "Must use array-based Runtime.exec to prevent shell injection",
            drmContent.contains("arrayOf(")
        )
        assertFalse(
            "Must NOT use 'sh -c' pattern for injection command",
            drmContent.contains("\"sh\", \"-c\"")
        )
    }

    @Test
    fun testLogsInjectionExitCode() {
        assertTrue(
            "Must log injection exit code for debugging",
            drmContent.contains("exit=") || drmContent.contains("exitCode")
        )
    }

    // ================================
    // Service discovery
    // ================================

    @Test
    fun testSearchesAidlServiceNames() {
        assertTrue(
            "Must search AIDL DRM service names (Android 13+)",
            drmContent.contains("android.hardware.drm.IDrmFactory/widevine")
        )
    }

    @Test
    fun testSearchesHidlServiceNames() {
        assertTrue(
            "Must search HIDL DRM service names (Android 12)",
            drmContent.contains("android.hardware.drm@1.4") || drmContent.contains("android.hardware.drm@1.3")
        )
    }

    @Test
    fun testSearchesClearkeyService() {
        assertTrue(
            "Must search ClearKey DRM service as fallback",
            drmContent.contains("clearkey")
        )
    }

    @Test
    fun testSearchesMultipleDrmProcessNames() {
        assertTrue(
            "Must search AIDL process name",
            drmContent.contains("android.hardware.drm-service.widevine")
        )
        assertTrue(
            "Must search HIDL process name",
            drmContent.contains("android.hardware.drm@1.4-service.widevine")
        )
        assertTrue(
            "Must search legacy mediadrmserver process",
            drmContent.contains("mediadrmserver")
        )
    }

    @Test
    fun testSearchesHidl12ServiceProcess() {
        assertTrue(
            "Must search HIDL 1.2 DRM process for older devices",
            drmContent.contains("android.hardware.drm@1.2-service.widevine")
        )
    }

    // ================================
    // Widevine spoofing correctness
    // ================================

    @Test
    fun testSpoofWidevineLevel() {
        assertTrue(
            "Must spoof Widevine security level based on configured level",
            drmContent.contains("ro.com.google.widevine.level") &&
            (drmContent.contains("L3") || drmContent.contains("L2"))
        )
    }

    @Test
    fun testSpoofsBothL3AndL2() {
        assertTrue("Must catch L3 responses", drmContent.contains("\"L3\""))
        assertTrue("Must catch L2 responses", drmContent.contains("\"L2\""))
    }

    @Test
    fun testRandomDrmOnBootFlag() {
        assertTrue(
            "Must check random_drm_on_boot flag file for DRM ID randomization",
            drmContent.contains("random_drm_on_boot")
        )
    }

    @Test
    fun testDrmIdIs32Bytes() {
        assertTrue(
            "Spoofed DRM device ID must be 32 bytes",
            drmContent.contains("ByteArray(32)")
        )
    }

    @Test
    fun testDrmFixGuard() {
        assertTrue(
            "Must check isDrmFixEnabled before intercepting",
            drmContent.contains("isDrmFixEnabled()")
        )
    }

    // ================================
    // Documentation
    // ================================

    @Test
    fun testHasKDocComment() {
        assertTrue(
            "DrmInterceptor must have KDoc documentation explaining its purpose",
            drmContent.contains("/**") && drmContent.contains("Widevine")
        )
    }

    // ================================
    // Helpers
    // ================================

    private fun extractMethod(source: String, methodName: String): String {
        val idx = source.indexOf("fun $methodName(")
        if (idx < 0) return ""
        var braces = 0
        var started = false
        val sb = StringBuilder()
        for (i in idx until source.length) {
            val c = source[i]
            sb.append(c)
            if (c == '{') { braces++; started = true }
            if (c == '}') { braces-- }
            if (started && braces == 0) break
        }
        return sb.toString()
    }
}
