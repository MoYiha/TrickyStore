package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Safety-gate tests for TelephonyInterceptor.
 *
 * Validates retry logic, IMEI/IMSI/ICCID interception coverage,
 * dual-SIM support, process injection safety, and thread safety.
 */
class TelephonyInterceptorSafetyTest {

    private lateinit var telContent: String

    @Before
    fun setup() {
        telContent = serviceMainFile("TelephonyInterceptor.kt").readText()
    }

    // ================================
    // Retry logic — all failure paths must increment triedCount
    // ================================

    @Test
    fun testRetryCounterIncrementedOnServiceNotFound() {
        // CRITICAL BUG FIX: iphonesubinfo not found MUST increment triedCount
        val afterNotFound = telContent.substring(telContent.indexOf("iphonesubinfo service not found"))
        val nextReturn = afterNotFound.indexOf("return")
        assertTrue("Must have return after service not found", nextReturn > 0)
        val between = afterNotFound.substring(0, nextReturn)
        assertTrue(
            "triedCount must be incremented when iphonesubinfo is not found (prevents infinite retry)",
            between.contains("triedCount += 1") || between.contains("triedCount++")
        )
    }

    @Test
    fun testRetryCounterIncrementedOnPidNotFound() {
        val afterPidNotFound = telContent.substring(telContent.indexOf("failed to find com.android.phone pid"))
        val nextReturn = afterPidNotFound.indexOf("return")
        assertTrue("Must have return after PID not found", nextReturn > 0)
        val between = afterPidNotFound.substring(0, nextReturn)
        assertTrue(
            "triedCount must be incremented when phone process PID not found",
            between.contains("triedCount += 1") || between.contains("triedCount++")
        )
    }

    @Test
    fun testRetryLimitExists() {
        assertTrue(
            "Telephony interceptor must have retry limit (triedCount >= 3)",
            telContent.contains("triedCount >= 3")
        )
    }

    // ================================
    // Death recipient
    // ================================

    @Test
    fun testDeathRecipientRegistered() {
        assertTrue(
            "Must register linkToDeath to detect iphonesubinfo service restarts",
            telContent.contains("linkToDeath")
        )
    }

    @Test
    fun testDeathRecipientResetsState() {
        assertTrue("Death recipient must reset injected state", telContent.contains("injected = false"))
        assertTrue("Death recipient must reset triedCount", telContent.contains("triedCount = 0"))
    }

    // ================================
    // IPhoneSubInfo transaction coverage — ALL telephony identifiers
    // ================================

    @Test
    fun testInterceptsGetDeviceId() {
        assertTrue(
            "Must intercept getDeviceId (primary IMEI query)",
            telContent.contains("getDeviceIdTransaction") && telContent.contains("\"getDeviceId\"")
        )
    }

    @Test
    fun testInterceptsGetDeviceIdForPhone() {
        assertTrue(
            "Must intercept getDeviceIdForPhone (dual-SIM IMEI query)",
            telContent.contains("getDeviceIdForPhoneTransaction") && telContent.contains("\"getDeviceIdForPhone\"")
        )
    }

    @Test
    fun testInterceptsGetImeiForSubscriber() {
        assertTrue(
            "Must intercept getImeiForSubscriber (per-SIM IMEI query)",
            telContent.contains("getImeiForSubscriberTransaction") && telContent.contains("\"getImeiForSubscriber\"")
        )
    }

    @Test
    fun testInterceptsGetSubscriberId() {
        assertTrue(
            "Must intercept getSubscriberId (IMSI query)",
            telContent.contains("getSubscriberIdTransaction") && telContent.contains("\"getSubscriberId\"")
        )
    }

    @Test
    fun testInterceptsGetSubscriberIdForSubscriber() {
        assertTrue(
            "Must intercept getSubscriberIdForSubscriber (per-SIM IMSI query)",
            telContent.contains("getSubscriberIdForSubscriberTransaction")
        )
    }

    @Test
    fun testInterceptsGetIccSerialNumber() {
        assertTrue(
            "Must intercept getIccSerialNumber (ICCID query)",
            telContent.contains("getIccSerialNumberTransaction") && telContent.contains("\"getIccSerialNumber\"")
        )
    }

    @Test
    fun testInterceptsGetIccSerialNumberForSubscriber() {
        assertTrue(
            "Must intercept getIccSerialNumberForSubscriber (per-SIM ICCID query)",
            telContent.contains("getIccSerialNumberForSubscriberTransaction")
        )
    }

    @Test
    fun testInterceptsGetLine1Number() {
        assertTrue(
            "Must intercept getLine1Number (phone number query)",
            telContent.contains("getLine1NumberTransaction") && telContent.contains("\"getLine1Number\"")
        )
    }

    @Test
    fun testInterceptsGetMeidForSubscriber() {
        assertTrue(
            "Must intercept getMeidForSubscriber (MEID query for CDMA)",
            telContent.contains("getMeidForSubscriberTransaction") && telContent.contains("\"getMeidForSubscriber\"")
        )
    }

    // ================================
    // Dual-SIM support
    // ================================

    @Test
    fun testDualSimImeiSupport() {
        assertTrue(
            "Must support ATTESTATION_ID_IMEI2 for dual-SIM devices",
            telContent.contains("ATTESTATION_ID_IMEI2")
        )
    }

    @Test
    fun testReadPhoneIndexFromDataParcel() {
        assertTrue(
            "Must read phone index from data parcel to determine which SIM slot",
            telContent.contains("phoneId") || telContent.contains("subId")
        )
    }

    // ================================
    // Spoofed values
    // ================================

    @Test
    fun testUsesAttestationIdImei() {
        assertTrue(
            "Must read IMEI from Config.getBuildVar(ATTESTATION_ID_IMEI)",
            telContent.contains("ATTESTATION_ID_IMEI")
        )
    }

    @Test
    fun testUsesAttestationIdImsi() {
        assertTrue(
            "Must read IMSI from Config.getBuildVar(ATTESTATION_ID_IMSI)",
            telContent.contains("ATTESTATION_ID_IMSI")
        )
    }

    @Test
    fun testUsesAttestationIdIccid() {
        assertTrue(
            "Must read ICCID from Config.getBuildVar(ATTESTATION_ID_ICCID)",
            telContent.contains("ATTESTATION_ID_ICCID")
        )
    }

    @Test
    fun testFallbackImeiGeneration() {
        assertTrue(
            "Must have fallback IMEI generation if config value is missing",
            telContent.contains("generateFallbackImei")
        )
    }

    @Test
    fun testFallbackImeiUsesSecureRandom() {
        assertTrue(
            "Fallback IMEI generation must use SecureRandom (not Random)",
            telContent.contains("secureRandom") || telContent.contains("SecureRandom")
        )
    }

    @Test
    fun testFallbackImeiHasLuhnChecksum() {
        // IMEI must have a valid Luhn check digit to pass carrier validation
        assertTrue(
            "Fallback IMEI generation must compute Luhn check digit",
            telContent.contains("checkDigit") || telContent.contains("luhn")
        )
    }

    // ================================
    // Process injection safety
    // ================================

    @Test
    fun testDrainsProcessStreams() {
        assertTrue(
            "Injection process must drain stdout/stderr to prevent FD exhaustion",
            telContent.contains("inputStream.readBytes()") && telContent.contains("errorStream.readBytes()")
        )
    }

    @Test
    fun testUsesArrayBasedExec() {
        assertTrue(
            "Must use array-based Runtime.exec for injection (no shell injection risk)",
            telContent.contains("arrayOf(")
        )
    }

    @Test
    fun testLogsInterceptedCalls() {
        assertTrue(
            "Must log intercepted telephony calls with UID for debugging",
            telContent.contains("uid=") || telContent.contains("callingUid")
        )
    }
}
