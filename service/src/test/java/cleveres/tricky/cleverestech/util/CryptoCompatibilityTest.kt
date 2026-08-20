package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CboxWireLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64

class CryptoCompatibilityTest {
    @Test
    fun `CTSB v1 and v2 golden vectors decrypt identically`() {
        val expected = "{\"version\":1,\"files\":{\"target.txt\":\"com.example.app\\n\"}}".toByteArray()
        val legacy = Base64.getDecoder().decode(CTSB_V1)
        val current = Base64.getDecoder().decode(CTSB_V2)
        try {
            assertTrue(BackupEncryptor.isEncryptedBackup(legacy))
            assertTrue(BackupEncryptor.isEncryptedBackup(current))
            assertArrayEquals(expected, ManagedBackupCryptoOracle.decrypt(legacy, PASSWORD))
            assertArrayEquals(expected, ManagedBackupCryptoOracle.decrypt(current, PASSWORD))
        } finally {
            expected.fill(0)
            legacy.fill(0)
            current.fill(0)
        }
    }

    @Test
    fun `CTSB v2 authenticates header and ciphertext`() {
        val original = Base64.getDecoder().decode(CTSB_V2)
        try {
            val versionTamper = original.clone()
            versionTamper[7] = 1
            assertDecryptFails(versionTamper)

            val saltTamper = original.clone()
            saltTamper[8] = (saltTamper[8].toInt() xor 1).toByte()
            assertDecryptFails(saltTamper)

            val ivTamper = original.clone()
            ivTamper[24] = (ivTamper[24].toInt() xor 1).toByte()
            assertDecryptFails(ivTamper)

            val ciphertextTamper = original.clone()
            ciphertextTamper[ciphertextTamper.lastIndex - 16] =
                (ciphertextTamper[ciphertextTamper.lastIndex - 16].toInt() xor 1).toByte()
            assertDecryptFails(ciphertextTamper)
        } finally {
            original.fill(0)
        }
    }

    @Test
    fun `CBOX v1 and v2 golden vectors preserve payload semantics`() {
        for (encoded in listOf(CBOX_V1, CBOX_V2)) {
            val bytes = Base64.getDecoder().decode(encoded)
            try {
                assertTrue(CboxDecryptor.hasSupportedEnvelopeHeader(bytes))
                val payload = ManagedCboxCryptoOracle.decrypt(ByteArrayInputStream(bytes), PASSWORD)
                assertNotNull(payload)
                requireNotNull(payload)
                assertEquals("CleveresTricky golden", payload.author)
                assertEquals(
                    "<AndroidAttestation NumberOfKeyboxes=\"0\"></AndroidAttestation>",
                    payload.xmlContent,
                )
                assertEquals("", payload.signatureBase64)
                assertEquals(2, payload.signatureVersion)
            } finally {
                bytes.fill(0)
            }
        }
    }

    @Test
    fun `CBOX v2 rejects header ciphertext truncation and wrong password`() {
        val original = Base64.getDecoder().decode(CBOX_V2)
        try {
            val headerTamper = original.clone()
            headerTamper[8] = (headerTamper[8].toInt() xor 1).toByte()
            assertNull(ManagedCboxCryptoOracle.decrypt(ByteArrayInputStream(headerTamper), PASSWORD))

            val ciphertextTamper = original.clone()
            ciphertextTamper[ciphertextTamper.lastIndex - 16] =
                (ciphertextTamper[ciphertextTamper.lastIndex - 16].toInt() xor 1).toByte()
            assertNull(ManagedCboxCryptoOracle.decrypt(ByteArrayInputStream(ciphertextTamper), PASSWORD))

            assertNull(ManagedCboxCryptoOracle.decrypt(ByteArrayInputStream(original), "wrong password"))
            assertNull(
                ManagedCboxCryptoOracle.decrypt(
                    ByteArrayInputStream(original.copyOf(original.size - 1)),
                    PASSWORD,
                ),
            )
        } finally {
            original.fill(0)
        }
    }

    @Test
    fun `CBOX header classifier is bounded and version aware`() {
        assertFalse(CboxDecryptor.hasSupportedEnvelopeHeader(ByteArray(0)))
        assertFalse(CboxDecryptor.hasSupportedEnvelopeHeader("CBOX".toByteArray()))
        val current = Base64.getDecoder().decode(CBOX_V2)
        try {
            val unsupported = current.clone()
            unsupported[7] = 3
            assertFalse(CboxDecryptor.hasSupportedEnvelopeHeader(unsupported))
        } finally {
            current.fill(0)
        }
    }

    @Test
    fun `CBOX header classifier accepts the full wire bound only`() {
        val bounded = ByteArray(CboxWireLimits.MAX_BYTES)
        val oversized = ByteArray(CboxWireLimits.MAX_BYTES + 1)
        try {
            "CBOX".toByteArray(StandardCharsets.US_ASCII).copyInto(bounded)
            bounded[7] = 2
            "CBOX".toByteArray(StandardCharsets.US_ASCII).copyInto(oversized)
            oversized[7] = 2

            assertTrue(CboxDecryptor.hasSupportedEnvelopeHeader(bounded))
            assertFalse(CboxDecryptor.hasSupportedEnvelopeHeader(oversized))
        } finally {
            bounded.fill(0)
            oversized.fill(0)
        }
    }

    private fun assertDecryptFails(bytes: ByteArray) {
        try {
            var failed = false
            try {
                ManagedBackupCryptoOracle.decrypt(bytes, PASSWORD)
            } catch (_: IOException) {
                failed = true
            } catch (_: Exception) {
                failed = true
            }
            assertTrue("tampered CTSB payload must fail closed", failed)
        } finally {
            bytes.fill(0)
        }
    }

    companion object {
        private const val PASSWORD = "correct horse battery staple"

        private const val CTSB_V1 =
            "Q1RTQgAAAAEAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPBYdDdFyqlYeaU/mul01QMGsRn7g0MjLdOskpN97GWZ5fNXsQE5H+FldOlDg4HvENUIQC5reyWvff34y4iedwVcfVEtlNB"
        private const val CTSB_V2 =
            "Q1RTQgAAAAIAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPBYdDdFyqlYeaU/mul01QMGsRn7g0MjLdOskpN97GWZ5fNXsQE5H+FldOlDg4HvENUIQC5rexM7K0B5tNer0Cjko6vCq2Z"
        private const val CBOX_V1 =
            "Q0JPWAAAAAEAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPWcdbGETfpef7mviy130oMGrIv/EwTlOVOuFIH5qfAaY+XUMc2qXWTgNu7FkkT/w9lEwrpv/iFQNyu/EsamoACXPaOVKKg+oGNsVLwNRNN4Gth46JQOziUU1/B3Fen+4BvKg9VtB9H4xnPi4AX+qMZHYhaW8ysgOQaSFcJy59C9IckzAalbsWXcjdsX8r1kr/KBOEALbqGa941n5vAlQEX5P77BBTF"
        private const val CBOX_V2 =
            "Q0JPWAAAAAIAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPWcdbGETfpef7mviy130oMGrIv/EwTlOVOuFIH5qfAaY+XUMc2qXWTgNu7FkkT/w9lEwrpv/iFQNyu/EsamoACXPaOVKKg+oGNsVLwNRNN4Gth46JQOziUU1/B3Fen+4BvKg9VtB9H4xnPi4AX+qMZHYhaW8ysgOQaSFcJy59C9IckzAalbsWXcjdsX8r1kr/KBOEALbqYmlPfNbKQEZdEZacWRvO3"
    }
}
