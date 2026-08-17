package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import java.io.InputStreamReader
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxJcaAdapterTest {
    @Test
    fun `PKCS8 adapter matches managed EC oracle`() {
        assertAdapterMatchesLegacy(
            resource = "/keybox/valid_ec.xml",
            filename = "valid_ec.xml",
            declaredAlgorithm = "ecdsa",
            expectedAlgorithm = "EC",
        )
    }

    @Test
    fun `PKCS8 adapter matches managed RSA oracle`() {
        assertAdapterMatchesLegacy(
            resource = "/keybox/valid_rsa.xml",
            filename = "valid_rsa.xml",
            declaredAlgorithm = "RSA",
            expectedAlgorithm = "RSA",
        )
    }

    @Test
    fun `algorithm mismatch fails closed and wipes private DER`() {
        silenceLogger()
        val legacy = readLegacyFixture("/keybox/valid_ec.xml", "valid_ec.xml").single()
        val document =
            KeyboxWire.Document(
                1,
                1,
                listOf(
                    KeyboxWire.RawKey(
                        "RSA",
                        legacy.keyPair().private.encoded,
                        legacy.certificates().map(::certificatePem),
                    ),
                ),
            )

        assertTrue(KeyboxJcaAdapter.materialize(document, "mismatch.xml").isEmpty())
        assertTrue(document.keys.single().privateKeyPkcs8.all { it == 0.toByte() })
    }

    @Test
    fun `corrupted private DER fails closed and is wiped`() {
        silenceLogger()
        val legacy = readLegacyFixture("/keybox/valid_ec.xml", "valid_ec.xml").single()
        val corrupted = legacy.keyPair().private.encoded
        corrupted[0] = 0x31
        val document =
            KeyboxWire.Document(
                1,
                1,
                listOf(
                    KeyboxWire.RawKey(
                        "EC",
                        corrupted,
                        legacy.certificates().map(::certificatePem),
                    ),
                ),
            )

        assertTrue(KeyboxJcaAdapter.materialize(document, "corrupt.xml").isEmpty())
        assertTrue(corrupted.all { it == 0.toByte() })
    }

    private fun assertAdapterMatchesLegacy(
        resource: String,
        filename: String,
        declaredAlgorithm: String,
        expectedAlgorithm: String,
    ) {
        silenceLogger()
        val legacy = readLegacyFixture(resource, filename).single()
        val privateDer = legacy.keyPair().private.encoded
        val migratedPrivate = privateDer.copyOf()
        val document =
            KeyboxWire.Document(
                declaredKeyboxes = 1,
                keyboxCount = 1,
                keys =
                    listOf(
                        KeyboxWire.RawKey(
                            algorithm = declaredAlgorithm,
                            privateKeyPkcs8 = migratedPrivate,
                            certificatesPem = legacy.certificates().map(::certificatePem),
                        ),
                    ),
            )

        val migrated = KeyboxJcaAdapter.materialize(document, filename).single()
        assertEquals(expectedAlgorithm, migrated.keyPair().private.algorithm)
        assertArrayEquals(legacy.keyPair().public.encoded, migrated.keyPair().public.encoded)
        assertArrayEquals(legacy.keyPair().private.encoded, migrated.keyPair().private.encoded)
        assertEquals(legacy.certificates().size, migrated.certificates().size)
        for (index in legacy.certificates().indices) {
            assertArrayEquals(
                legacy.certificates()[index].encoded,
                migrated.certificates()[index].encoded,
            )
        }
        assertTrue(migratedPrivate.all { it == 0.toByte() })
        privateDer.fill(0)
    }

    private fun readLegacyFixture(
        resource: String,
        filename: String,
    ): List<CertHack.KeyBox> {
        val stream = requireNotNull(javaClass.getResourceAsStream(resource))
        return InputStreamReader(stream, Charsets.UTF_8).use {
            CertHack.parseKeyboxXml(it, filename)
        }
    }

    private fun certificatePem(certificate: java.security.cert.Certificate): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
        return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----"
    }

    private fun silenceLogger() {
        Logger.setImpl(
            object : Logger.LogImpl {
                override fun d(tag: String, msg: String) = Unit

                override fun e(tag: String, msg: String) = Unit

                override fun e(tag: String, msg: String, t: Throwable?) = Unit

                override fun i(tag: String, msg: String) = Unit
            },
        )
    }
}
