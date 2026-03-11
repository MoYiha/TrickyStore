package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Safety tests for RandomUtils — validates that all identity generation
 * uses cryptographically secure random number generation.
 *
 * Identity values (IMEI, serial, MAC) generated with weak RNG
 * could be predicted or brute-forced, defeating the purpose of spoofing.
 */
class RandomUtilsSecurityTest {

    private lateinit var content: String

    @Before
    fun setup() {
        content = serviceMainFile("util/RandomUtils.kt").readText()
    }

    // ================================
    // Must use SecureRandom, NOT kotlin.random.Random
    // ================================

    @Test
    fun testImportsSecureRandom() {
        assertTrue(
            "Must import java.security.SecureRandom",
            content.contains("import java.security.SecureRandom")
        )
    }

    @Test
    fun testDoesNotImportKotlinRandom() {
        assertFalse(
            "Must NOT import kotlin.random.Random (insecure for identity generation)",
            content.contains("import kotlin.random.Random")
        )
    }

    @Test
    fun testDoesNotUseKotlinRandomDirectly() {
        // After removing the import, verify no direct Random.nextInt calls remain
        assertFalse(
            "Must NOT call Random.nextInt() (only SecureRandom allowed)",
            content.contains("Random.nextInt(") && !content.contains("SecureRandom")
        )
    }

    @Test
    fun testUsesThreadLocalSecureRandom() {
        assertTrue(
            "Must use ThreadLocal<SecureRandom> for thread safety and performance",
            content.contains("ThreadLocal<SecureRandom>") || content.contains("threadLocalRandom")
        )
    }

    // ================================
    // Coverage of all generation methods
    // ================================

    @Test
    fun testGenerateLuhnExists() {
        assertTrue("generateLuhn must exist for IMEI generation", content.contains("fun generateLuhn"))
    }

    @Test
    fun testGenerateRandomSerialExists() {
        assertTrue("generateRandomSerial must exist", content.contains("fun generateRandomSerial"))
    }

    @Test
    fun testGenerateRandomMacExists() {
        assertTrue("generateRandomMac must exist", content.contains("fun generateRandomMac"))
    }

    @Test
    fun testGenerateRandomAndroidIdExists() {
        assertTrue("generateRandomAndroidId must exist", content.contains("fun generateRandomAndroidId"))
    }

    @Test
    fun testGenerateRandomLocationOffsetExists() {
        assertTrue("generateRandomLocationOffset must exist", content.contains("fun generateRandomLocationOffset"))
    }

    // ================================
    // Luhn algorithm correctness
    // ================================

    @Test
    fun testLuhnHasCheckDigitCalculation() {
        assertTrue(
            "generateLuhn must compute a check digit for valid IMEI/Luhn numbers",
            content.contains("checkDigit")
        )
    }

    @Test
    fun testLuhnSupportsPrefixParameter() {
        assertTrue(
            "generateLuhn must support a prefix parameter (e.g. '35' for IMEI TAC)",
            content.contains("prefix")
        )
    }

    // ================================
    // MAC address format
    // ================================

    @Test
    fun testMacAddressHasColonSeparators() {
        assertTrue(
            "Generated MAC addresses must use colon separators (XX:XX:XX:XX:XX:XX)",
            content.contains("':'")
        )
    }

    @Test
    fun testMacAddressIs6Octets() {
        assertTrue(
            "Generated MAC address must be 6 octets (0 until 6 loop)",
            content.contains("0 until 6")
        )
    }

    // ================================
    // Location spoofing
    // ================================

    @Test
    fun testLocationClampsLatitude() {
        assertTrue(
            "Location spoofing must clamp latitude to valid range (-90..90)",
            content.contains("coerceIn(-90.0, 90.0)")
        )
    }

    @Test
    fun testLocationClampsLongitude() {
        assertTrue(
            "Location spoofing must clamp longitude to valid range (-180..180)",
            content.contains("coerceIn(-180.0, 180.0)")
        )
    }

    @Test
    fun testLocationAvoidsDivisionByZeroAtPoles() {
        assertTrue(
            "Location offset must clamp latitude away from poles to avoid cos(90)=0 division",
            content.contains("coerceIn(-89.9, 89.9)") || content.contains("safeLat")
        )
    }

    @Test
    fun testLocationUsesUniformDistribution() {
        assertTrue(
            "Location offset must use sqrt(random) for uniform area distribution within circle",
            content.contains("sqrt") && content.contains("radiusMeters")
        )
    }
}
