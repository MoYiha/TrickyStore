package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate IMEI/identity spoofing code correctness.
 *
 * These tests verify that the Telephony interceptor, C++ binder interceptor,
 * and configuration system correctly handle device identity spoofing across
 * all supported transaction codes and process boundaries.
 */
class SpoofingCodeSafetyTest {

    private lateinit var telephonyContent: String
    private lateinit var binderInterceptorCppContent: String
    private lateinit var configContent: String
    private lateinit var spoofBuildVarsContent: String

    @Before
    fun setup() {
        telephonyContent = serviceMainFile("TelephonyInterceptor.kt").readText()
        binderInterceptorCppContent = moduleCppFile("binder_interceptor.cpp").readText()
        configContent = serviceMainFile("Config.kt").readText()
        spoofBuildVarsContent = moduleTemplateFile("spoof_build_vars").readText()
    }

    // ================================
    // Telephony interceptor coverage
    // ================================

    @Test
    fun testInterceptsGetDeviceId() {
        assertTrue(
            "Must intercept getDeviceId for IMEI spoofing",
            telephonyContent.contains("getDeviceIdTransaction")
        )
    }

    @Test
    fun testInterceptsGetDeviceIdForPhone() {
        assertTrue(
            "Must intercept getDeviceIdForPhone for multi-SIM IMEI spoofing",
            telephonyContent.contains("getDeviceIdForPhoneTransaction")
        )
    }

    @Test
    fun testInterceptsGetImeiForSubscriber() {
        assertTrue(
            "Must intercept getImeiForSubscriber for per-SIM IMEI spoofing",
            telephonyContent.contains("getImeiForSubscriberTransaction")
        )
    }

    @Test
    fun testInterceptsGetSubscriberId() {
        assertTrue(
            "Must intercept getSubscriberId for IMSI spoofing",
            telephonyContent.contains("getSubscriberIdTransaction")
        )
    }

    @Test
    fun testInterceptsGetIccSerialNumber() {
        assertTrue(
            "Must intercept getIccSerialNumber for ICCID spoofing",
            telephonyContent.contains("getIccSerialNumberTransaction")
        )
    }

    @Test
    fun testInterceptsGetLine1Number() {
        assertTrue(
            "Must intercept getLine1Number for phone number spoofing",
            telephonyContent.contains("getLine1NumberTransaction")
        )
    }

    @Test
    fun testInterceptsGetMeid() {
        assertTrue(
            "Must intercept getMeidForSubscriber for MEID spoofing",
            telephonyContent.contains("getMeidForSubscriberTransaction")
        )
    }

    // ================================
    // IMEI generation correctness
    // ================================

    @Test
    fun testFallbackImeiHasLuhnChecksum() {
        assertTrue(
            "Fallback IMEI generator must compute Luhn checksum",
            telephonyContent.contains("checkDigit") && telephonyContent.contains("sum % 10")
        )
    }

    @Test
    fun testFallbackImeiStartsWith35() {
        assertTrue(
            "Fallback IMEI must start with '35' (valid TAC prefix)",
            telephonyContent.contains("sb.append(\"35\")")
        )
    }

    @Test
    fun testFallbackImeiLength15() {
        // 2 (prefix) + 12 (random) + 1 (check) = 15 digits
        assertTrue(
            "Fallback IMEI must generate 12 random digits after prefix (15 total with check digit)",
            telephonyContent.contains("0 until 12")
        )
    }

    @Test
    fun testFallbackImsiHasValidMccMnc() {
        assertTrue(
            "Fallback IMSI must start with valid MCC (310 = US)",
            telephonyContent.contains("\"310\"")
        )
        assertTrue(
            "Fallback IMSI must have valid MNC after MCC",
            telephonyContent.contains("\"260\"")
        )
    }

    @Test
    fun testFallbackIccidHasStandardPrefix() {
        assertTrue(
            "Fallback ICCID must start with '8901' (standard SIM prefix)",
            telephonyContent.contains("\"8901\"")
        )
    }

    // ================================
    // C++ property interception
    // ================================

    @Test
    fun testCppInterceptsImeiProperty() {
        assertTrue(
            "C++ binder_interceptor must hook persist.radio.imei property",
            binderInterceptorCppContent.contains("persist.radio.imei")
        )
    }

    @Test
    fun testCppInterceptsVendorImeiProperty() {
        assertTrue(
            "C++ binder_interceptor must hook vendor.ril.imei property",
            binderInterceptorCppContent.contains("vendor.ril.imei")
        )
    }

    @Test
    fun testCppUsesSystemPropertyHook() {
        assertTrue(
            "C++ must hook __system_property_get for property interception",
            binderInterceptorCppContent.contains("__system_property_get")
        )
    }

    @Test
    fun testCppHooksBinderIoctl() {
        assertTrue(
            "C++ must hook ioctl for binder transaction interception",
            binderInterceptorCppContent.contains("ioctl") || binderInterceptorCppContent.contains("new_ioctl")
        )
    }

    // ================================
    // Configuration completeness
    // ================================

    @Test
    fun testConfigHasAttestationIdImei() {
        assertTrue(
            "Config must support ATTESTATION_ID_IMEI build var",
            configContent.contains("ATTESTATION_ID_IMEI")
        )
    }

    @Test
    fun testConfigHasNeedHack() {
        assertTrue(
            "Config must have needHack function to control per-app spoofing",
            configContent.contains("fun needHack")
        )
    }

    @Test
    fun testConfigHasNeedGenerate() {
        assertTrue(
            "Config must have needGenerate function to control cert generation",
            configContent.contains("fun needGenerate")
        )
    }

    @Test
    fun testConfigHasRandomImei() {
        assertTrue(
            "Config must support random IMEI on boot",
            configContent.contains("randomImei") || configContent.contains("random_on_boot") || configContent.contains("generateLuhn")
        )
    }

    // ================================
    // Template completeness
    // ================================

    @Test
    fun testSpoofBuildVarsHasFingerprint() {
        assertTrue(
            "spoof_build_vars must define FINGERPRINT for device identity",
            spoofBuildVarsContent.contains("FINGERPRINT=")
        )
    }

    @Test
    fun testSpoofBuildVarsHasBootState() {
        assertTrue(
            "spoof_build_vars must define verified boot state as green",
            spoofBuildVarsContent.contains("verifiedbootstate=green")
        )
    }

    @Test
    fun testSpoofBuildVarsHasLockedBootloader() {
        assertTrue(
            "spoof_build_vars must define locked bootloader",
            spoofBuildVarsContent.contains("flash.locked=1")
        )
    }

    // ================================
    // Telephony injection safety
    // ================================

    @Test
    fun testTelephonyUsesSecureRandom() {
        assertTrue(
            "Telephony interceptor must use SecureRandom for IMEI generation (not Random)",
            telephonyContent.contains("SecureRandom")
        )
        assertFalse(
            "Telephony interceptor must NOT use java.util.Random (insecure)",
            telephonyContent.contains("java.util.Random()")
        )
    }

    @Test
    fun testTelephonyHandlesDeathRecipient() {
        assertTrue(
            "Telephony interceptor must register death recipient to detect service crashes",
            telephonyContent.contains("linkToDeath")
        )
    }

    @Test
    fun testTelephonyResetsOnDeath() {
        assertTrue(
            "Telephony interceptor must reset injection state when service dies",
            telephonyContent.contains("injected = false")
        )
    }

    @Test
    fun testTelephonyHasRetryLimit() {
        assertTrue(
            "Telephony interceptor must have a retry limit to prevent infinite injection loops",
            telephonyContent.contains("triedCount >= 3") || telephonyContent.contains("triedCount >=")
        )
    }
}
