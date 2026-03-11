package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Safety tests for BackupEncryptor crypto implementation.
 *
 * Validates key derivation security, resource management,
 * password material cleanup, and encryption parameters.
 */
class BackupEncryptorSafetyTest {

    private lateinit var encContent: String

    @Before
    fun setup() {
        encContent = serviceMainFile("util/BackupEncryptor.kt").readText()
    }

    // ================================
    // Crypto parameters
    // ================================

    @Test
    fun testUsesAes256Gcm() {
        assertTrue(
            "Must use AES/GCM/NoPadding for authenticated encryption",
            encContent.contains("AES/GCM/NoPadding")
        )
    }

    @Test
    fun testUsesPbkdf2WithHmacSha256() {
        assertTrue(
            "Must use PBKDF2WithHmacSHA256 for key derivation",
            encContent.contains("PBKDF2WithHmacSHA256")
        )
    }

    @Test
    fun testIterationCountAtLeast200k() {
        // PBKDF2 should use at least 200,000 iterations for 2024+ security
        val regex = Regex("ITERATION_COUNT\\s*=\\s*(\\d+)")
        val match = regex.find(encContent)
        assertTrue("Must define ITERATION_COUNT", match != null)
        val count = match!!.groupValues[1].toInt()
        assertTrue(
            "PBKDF2 iteration count must be >= 200000 (got $count)",
            count >= 200000
        )
    }

    @Test
    fun testKeyLength256() {
        assertTrue(
            "Must use 256-bit keys for AES-256",
            encContent.contains("KEY_LENGTH = 256")
        )
    }

    @Test
    fun testSaltLength16() {
        assertTrue(
            "Must use 16-byte salt for PBKDF2",
            encContent.contains("SALT_LENGTH = 16")
        )
    }

    @Test
    fun testIvLength12() {
        assertTrue(
            "Must use 12-byte IV for AES-GCM (NIST recommended)",
            encContent.contains("IV_LENGTH = 12")
        )
    }

    @Test
    fun testGcmTagLength128() {
        assertTrue(
            "Must use 128-bit GCM authentication tag",
            encContent.contains("GCMParameterSpec(128")
        )
    }

    // ================================
    // Key material cleanup
    // ================================

    @Test
    fun testKeyBytesCleared() {
        assertTrue(
            "Derived key bytes must be zeroed after use to prevent memory exposure",
            encContent.contains("keyBytes.fill(0)")
        )
    }

    @Test
    fun testPasswordCharArrayCleared() {
        assertTrue(
            "Password char array must be zeroed after key derivation",
            encContent.contains("passwordChars.fill") || encContent.contains("fill('\\u0000')")
        )
    }

    // ================================
    // Random generation
    // ================================

    @Test
    fun testUsesSecureRandomForSalt() {
        assertTrue(
            "Must use SecureRandom to generate salt",
            encContent.contains("SecureRandom()") && encContent.contains("salt")
        )
    }

    @Test
    fun testUsesSecureRandomForIv() {
        assertTrue(
            "Must use SecureRandom to generate IV",
            encContent.contains("SecureRandom()") && encContent.contains("iv")
        )
    }

    // ================================
    // Resource management
    // ================================

    @Test
    fun testDecryptClosesStream() {
        assertTrue(
            "decrypt() must close DataInputStream (use .use{} or try-with-resources)",
            encContent.contains(".use {") || encContent.contains(".use{")
        )
    }

    @Test
    fun testMagicBytesValidated() {
        assertTrue(
            "Must validate CTSB magic bytes before decryption",
            encContent.contains("Not a CTSB encrypted backup")
        )
    }

    @Test
    fun testVersionValidated() {
        assertTrue(
            "Must validate CTSB version before decryption",
            encContent.contains("Unsupported CTSB version")
        )
    }
}
