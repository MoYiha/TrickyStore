package cleveres.tricky.cleverestech.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CryptoEdgeCompatibilityTest {
    @Test
    fun `PBKDF2 password encoding is UTF-8 compatible for non ASCII input`() {
        val encrypted = Base64.getDecoder().decode(UNICODE_CTSB_V2)
        val expected = "unicode-password".toByteArray(Charsets.UTF_8)
        try {
            assertArrayEquals(expected, ManagedBackupCryptoOracle.decrypt(encrypted, "pässwörd🔐"))
        } finally {
            encrypted.fill(0)
            expected.fill(0)
        }
    }

    @Test
    fun `CBOX signature v2 verifies RSA over byte length framed UTF-8 message`() {
        val payload =
            ManagedCboxCryptoOracle.CboxPayload(
                author = "Δ-author",
                xmlContent = "<AndroidAttestation NumberOfKeyboxes=\"0\"/>",
                signatureBase64 = RSA_V2_SIGNATURE,
                signatureVersion = 2,
            )

        assertTrue(ManagedCboxCryptoOracle.verifySignature(payload, RSA_PUBLIC_KEY))
        assertFalse(ManagedCboxCryptoOracle.verifySignature(payload.copy(author = "Δ-author!"), RSA_PUBLIC_KEY))
        assertFalse(ManagedCboxCryptoOracle.verifySignature(payload, EC_PUBLIC_KEY))
    }

    @Test
    fun `CBOX signature v1 verifies ECDSA over direct UTF-8 concatenation`() {
        val payload =
            ManagedCboxCryptoOracle.CboxPayload(
                author = "Δ-author",
                xmlContent = "<AndroidAttestation NumberOfKeyboxes=\"0\"/>",
                signatureBase64 = EC_V1_SIGNATURE,
                signatureVersion = 1,
            )

        assertTrue(ManagedCboxCryptoOracle.verifySignature(payload, EC_PUBLIC_KEY))
        assertFalse(
            ManagedCboxCryptoOracle.verifySignature(
                payload.copy(xmlContent = payload.xmlContent + " "),
                EC_PUBLIC_KEY,
            ),
        )
        assertFalse(ManagedCboxCryptoOracle.verifySignature(payload, RSA_PUBLIC_KEY))
    }

    companion object {
        private const val UNICODE_CTSB_V2 =
            "Q1RTQgAAAAIAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobPQ68RCKNeVd3YNhSJdkhC80HQxAsoEMGtGESspVQFfc="

        private const val RSA_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyC0vjOhT7Q5iJs2Kz0hH7d3jNZTbdYKUz7a9r4mu5YnlWpFpNgECL9r2ES/3KlI1EWDR/kSWnbP+zhuXHb/eXo9gDHQTx1G9EKqBHxi++gXhoHirdBTZfRR1nypLFxFar5iKv6QlPIpb8etuvV8lrysmC+nV3temRsrohibenakmqzw8yvnugIY6M2rF21v/2HYdj3BRKvniIK2/sQWPR8FsHwe22TdmOZscVEtN4t5p+PI2A9A9ICvNqo1NgdJt3fEw3TI55MdgZg3CwG4QwTqQ2+0Cc7Svm+QXAAMfFGyilRJT/qquGlZFUWVldA69hQx+uu2jcns9NhGSIZuiDwIDAQAB"
        private const val RSA_V2_SIGNATURE =
            "L0YXhejiCDuGNNTOUp3cjeHsYcEAEUa4CMXWZdHKKIRoVfeQ98h+6ebiXHImw3Ebc1z6PZEcJM1t4m/KZryWKKHdeqxxid0XlZyRg3KnFJd2i5Klz8R8B9gD7i+EfzWD+s1XiGPi/Sb1j1GfNgp+tsGLlj91GwZtNGJaLkKNBoyb2DBrP3vF9XnDSpo1jJBi6sa63c8sDCyyT3CoobYCcdbnc1+W2ggWYat5gy9NxY3aTt0B0aJC97E0v//QjCJToVDU38ty531IWiMNHdpBtbeXDlQfgIn+bg9dFVcYzFSzpUNxGye2N635Hz5WCUkTALnhJERHkWRlsMELmq2pSg=="
        private const val EC_PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1JxPSOlrdyKm0raMMZTeiV0WevPD6Nol0UdzGsWfpkwkz8HS3WaT1weN7FrMFimvq4QUJq9pwZ0hrO6/cy++Pg=="
        private const val EC_V1_SIGNATURE =
            "MEQCICfCTlaCRDuo9cg1SFXnf/u4Qict9SOgM3u28HoXNYtpAiBOkVG0WCmDfgMMe3Z0qIO/RtgB0D5Ca4B0IWM3CY+VMA=="
    }
}
