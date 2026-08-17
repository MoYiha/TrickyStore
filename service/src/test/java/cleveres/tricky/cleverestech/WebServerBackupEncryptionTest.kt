package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.BackupEncryptor
import cleveres.tricky.cleverestech.util.ManagedBackupCryptoOracle
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class WebServerBackupEncryptionTest {
    private lateinit var testDir: File
    private lateinit var configDir: File
    private lateinit var originalSecureFileImpl: SecureFileOperations

    @Before
    fun setUp() {
        testDir = File(System.getProperty("java.io.tmpdir"), "cleverestricky_enc_test_${System.currentTimeMillis()}")
        testDir.mkdirs()
        configDir = File(testDir, "config")
        configDir.mkdirs()
        originalSecureFileImpl = SecureFile.impl
        ManagedKeyboxParserOracle.install()

        SecureFile.impl =
            object : SecureFileOperations {
                override fun writeText(
                    file: File,
                    content: String,
                ) {
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                }

                override fun writeBytes(
                    file: File,
                    content: ByteArray,
                ) {
                    file.parentFile?.mkdirs()
                    file.writeBytes(content)
                }

                override fun writeStream(
                    file: File,
                    inputStream: java.io.InputStream,
                    limit: Long,
                ) {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { out ->
                        var total = 0L
                        val buf = ByteArray(8192)
                        var n: Int
                        while (inputStream.read(buf).also { n = it } != -1) {
                            if (limit > 0 && total + n > limit) throw java.io.IOException("Exceeds limit")
                            out.write(buf, 0, n)
                            total += n
                        }
                    }
                }

                override fun mkdirs(
                    file: File,
                    mode: Int,
                ) {
                    file.mkdirs()
                }

                override fun touch(
                    file: File,
                    mode: Int,
                ) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
            }
    }

    @After
    fun tearDown() {
        ManagedKeyboxParserOracle.reset()
        SecureFile.impl = originalSecureFileImpl
        testDir.deleteRecursively()
    }

    @Test
    fun testManagedOracleEncryptDecryptRoundTrip() {
        val original = "Hello, CTSB backup world!".toByteArray()
        val password = "s3cur3P@ss"
        val encrypted = ManagedBackupCryptoOracle.encrypt(original, password)
        val decrypted = ManagedBackupCryptoOracle.decrypt(encrypted, password)
        assertArrayEquals("Round-trip must produce identical bytes", original, decrypted)
    }

    @Test
    fun testEncryptedOutputStartsWithMagic() {
        val encrypted = ManagedBackupCryptoOracle.encrypt("data".toByteArray(), "pw")
        val magic = String(encrypted.copyOf(4), Charsets.US_ASCII)
        assertEquals("Encrypted backup must start with CTSB magic", BackupEncryptor.MAGIC, magic)
    }

    @Test
    fun testIsEncryptedBackupDetectsCtsbMagic() {
        val encrypted = ManagedBackupCryptoOracle.encrypt("data".toByteArray(), "pw")
        assertTrue("Must detect CTSB header as encrypted", BackupEncryptor.isEncryptedBackup(encrypted))
    }

    @Test
    fun testIsEncryptedBackupReturnsFalseForPlainZip() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
        assertFalse("Plain ZIP must not be detected as encrypted", BackupEncryptor.isEncryptedBackup(zipHeader))
    }

    @Test
    fun testIsEncryptedBackupReturnsFalseForShortData() {
        assertFalse("Empty bytes must not be detected as encrypted", BackupEncryptor.isEncryptedBackup(ByteArray(0)))
        assertFalse("Short bytes must not be detected as encrypted", BackupEncryptor.isEncryptedBackup(ByteArray(3)))
    }

    @Test
    fun testWrongPasswordThrows() {
        val encrypted = ManagedBackupCryptoOracle.encrypt("sensitive data".toByteArray(), "correctPassword")
        var threw = false
        try {
            ManagedBackupCryptoOracle.decrypt(encrypted, "wrongPassword")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("Decryption with wrong password must throw", threw)
    }

    @Test
    fun testAuthenticatedHeaderRejectsTampering() {
        val encrypted = ManagedBackupCryptoOracle.encrypt("sensitive data".toByteArray(), "correctPassword")
        encrypted[12] = (encrypted[12].toInt() xor 1).toByte()

        var threw = false
        try {
            ManagedBackupCryptoOracle.decrypt(encrypted, "correctPassword")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("Changing a v2 CTSB header byte must invalidate authentication", threw)
    }

    @Test
    fun testProductionDecryptRejectsInvalidMagicBeforeBackend() {
        val notCtsb = ByteArray(MINIMUM_CTSB_BYTES)
        notCtsb[0] = 0x50
        notCtsb[1] = 0x4B
        var threw = false
        try {
            BackupEncryptor.decrypt(notCtsb, "pw")
        } catch (e: IOException) {
            threw = true
        }
        assertTrue("Production guard must reject wrong magic without backend IPC", threw)
    }

    @Test
    fun testProductionDecryptRejectsUnsupportedVersionBeforeBackend() {
        val unsupported = ByteArray(MINIMUM_CTSB_BYTES)
        "CTSB".toByteArray(Charsets.US_ASCII).copyInto(unsupported)
        unsupported[7] = 3
        var threw = false
        try {
            BackupEncryptor.decrypt(unsupported, "pw")
        } catch (e: IOException) {
            threw = true
        }
        assertTrue("Production guard must reject unsupported versions without backend IPC", threw)
    }

    @Test
    fun testDifferentPasswordsProduceDifferentCiphertext() {
        val plaintext = "same data".toByteArray()
        val enc1 = ManagedBackupCryptoOracle.encrypt(plaintext, "password1")
        val enc2 = ManagedBackupCryptoOracle.encrypt(plaintext, "password2")
        assertFalse("Different passwords must produce different ciphertext", enc1.contentEquals(enc2))
    }

    @Test
    fun testTwoEncryptionsOfSamePlaintextDiffer() {
        // Each call uses a fresh random salt + IV.
        val plaintext = "same data".toByteArray()
        val enc1 = ManagedBackupCryptoOracle.encrypt(plaintext, "pw")
        val enc2 = ManagedBackupCryptoOracle.encrypt(plaintext, "pw")
        assertFalse("Two encryptions of the same data must produce different ciphertext (random salt/IV)", enc1.contentEquals(enc2))
    }

    @Test
    fun testFullBackupEncryptDecryptRestoreCycle() {
        File(configDir, "target.txt").writeText("com.example.app")
        File(configDir, "spoof_build_vars").writeText("MODEL=Pixel 9")
        val kbDir = File(configDir, "keyboxes")
        kbDir.mkdirs()
        File(kbDir, "kb1.xml").writeText(TestKeyboxFixtures.validEcKeyboxXml)

        val zipBytes = WebServer.createBackupZip(configDir)
        assertTrue("ZIP backup must not be empty", zipBytes.isNotEmpty())

        val password = "backupPass123"
        val encryptedBytes = ManagedBackupCryptoOracle.encrypt(zipBytes, password)
        assertTrue("Encrypted backup must start with CTSB", BackupEncryptor.isEncryptedBackup(encryptedBytes))

        configDir.deleteRecursively()
        configDir.mkdirs()

        val decryptedZip = ManagedBackupCryptoOracle.decrypt(encryptedBytes, password)
        WebServer.restoreBackupZip(configDir, ByteArrayInputStream(decryptedZip))

        assertEquals("com.example.app", File(configDir, "target.txt").readText())
        assertEquals("MODEL=Pixel 9", File(configDir, "spoof_build_vars").readText())
        assertEquals(TestKeyboxFixtures.validEcKeyboxXml, File(configDir, "keyboxes/kb1.xml").readText())
    }

    companion object {
        private const val MINIMUM_CTSB_BYTES = 4 + Int.SIZE_BYTES + 16 + 12 + 16
    }
}
