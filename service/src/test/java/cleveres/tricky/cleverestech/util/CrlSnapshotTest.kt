package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.CrlBackend
import cleveres.tricky.cleverestech.CrlWire
import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.keystore.CertHack
import java.io.IOException
import java.io.InputStreamReader
import java.security.cert.X509Certificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CrlSnapshotTest {
    @Before
    fun setUp() {
        silenceLogger()
    }

    @After
    fun tearDown() {
        CrlBackend.resetForTesting()
    }

    @Test
    fun `snapshot refreshes once then batches certificate queries with legacy status semantics`() {
        val keybox = readFixture("/keybox/valid_ec.xml", "valid_ec.xml")
        val certificate = keybox.certificates().first() as X509Certificate
        var refreshCalls = 0
        var queryCalls = 0
        CrlBackend.refreshOverride = { raw ->
            assertArrayEquals("opaque-crl".toByteArray(), raw)
            refreshCalls++
            CrlWire.Handle(3, 2, 7)
        }
        CrlBackend.queryOverride = { generation, queries ->
            assertEquals(3, generation)
            queryCalls++
            assertEquals(2 * keybox.certificates().size, queries.size)
            assertArrayEquals(
                certificate.serialNumber.toByteArray(),
                queries.first().serialTwosComplement,
            )
            assertArrayEquals(certificate.publicKey.encoded, queries.first().subjectPublicKeyInfo)
            CrlWire.Result(
                generation,
                BooleanArray(queries.size).also { bits -> bits[keybox.certificates().size] = true },
            )
        }

        val snapshot = requireNotNull(CrlSnapshot.parse("opaque-crl".toByteArray()))
        assertEquals(7, snapshot.normalizedEntryCount)
        assertEquals(
            listOf(KeyboxVerifier.Status.VALID, KeyboxVerifier.Status.REVOKED),
            snapshot.verify(listOf(keybox, keybox)),
        )
        assertEquals(1, refreshCalls)
        assertEquals(1, queryCalls)
    }

    @Test
    fun `empty certificate chain stays invalid without creating a query`() {
        val keybox = readFixture("/keybox/valid_ec.xml", "valid_ec.xml")
        val empty = CertHack.KeyBox(keybox.keyPair(), emptyList(), "empty.xml")
        CrlBackend.refreshOverride = { CrlWire.Handle(1, 0, 0) }
        CrlBackend.queryOverride = { _, _ -> throw AssertionError("empty chain must not produce CRL queries") }

        val snapshot = requireNotNull(CrlSnapshot.parse("{}".toByteArray()))
        assertEquals(listOf(KeyboxVerifier.Status.INVALID), snapshot.verify(listOf(empty)))
    }

    @Test
    fun `backend generation and count drift and oversized snapshots fail closed`() {
        CrlBackend.refreshOverride = { CrlWire.Handle(4, 1, 2) }
        CrlBackend.queryOverride = { _, queries -> CrlWire.Result(5, BooleanArray(queries.size)) }
        val keybox = readFixture("/keybox/valid_ec.xml", "valid_ec.xml")
        val snapshot = requireNotNull(CrlSnapshot.parse("{}".toByteArray()))
        assertNull(snapshot.verify(listOf(keybox)))

        CrlBackend.queryOverride = { generation, queries -> CrlWire.Result(generation, BooleanArray(queries.size + 1)) }
        assertNull(snapshot.verify(listOf(keybox)))
        assertNull(CrlSnapshot.parse(ByteArray(CrlWire.MAX_CRL_BYTES + 1)))
    }

    @Test
    fun `backend exception follows verifier error path`() {
        val keybox = readFixture("/keybox/valid_ec.xml", "valid_ec.xml")
        var refreshed = false
        CrlBackend.refreshOverride = {
            refreshed = true
            CrlWire.Handle(1, 1, 1)
        }
        CrlBackend.queryOverride = { _, _ -> throw IOException("provider unavailable") }

        val snapshot = requireNotNull(CrlSnapshot.parse("{}".toByteArray()))
        assertEquals(true, refreshed)
        assertNull(snapshot.verify(listOf(keybox)))
    }

    private fun readFixture(
        resource: String,
        filename: String,
    ): CertHack.KeyBox {
        val stream = requireNotNull(javaClass.getResourceAsStream(resource))
        return InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            CertHack.parseKeyboxXml(reader, filename).single()
        }
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
