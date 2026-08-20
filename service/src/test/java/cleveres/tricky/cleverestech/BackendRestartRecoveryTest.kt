package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import java.security.KeyPair
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendRestartRecoveryTest {
    private val identityA = NativeBackend.BackendIdentity(101, 0x1111, 0x2222)
    private val identityB = NativeBackend.BackendIdentity(202, 0x3333, 0x4444)

    @Before
    fun setUp() {
        NativeBackend.resetIdentityForTesting()
        BackendRecovery.resetForTesting()
        KeyboxActivation.resetForTesting()
        KeyboxLoader.resetForTesting()
        CertificateBackend.resetForTesting()
        CrlBackend.resetForTesting()
        ManagedOpaqueKeyOracle.reset()
    }

    @After
    fun tearDown() {
        CertificateBackend.resetForTesting()
        CrlBackend.resetForTesting()
        KeyboxLoader.resetForTesting()
        KeyboxActivation.resetForTesting()
        BackendRecovery.resetForTesting()
        NativeBackend.resetIdentityForTesting()
        ManagedOpaqueKeyOracle.reset()
    }

    @Test
    fun `backend A handles become stale and backend B recovers keyboxes crl and certificate rewrite once`() {
        NativeBackend.observeBackendIdentityForTesting(identityA)
        val original = keyboxForCurrentIdentity("backend-a.xml")
        var activeIds = emptyList<ByteArray>()
        KeyboxLoader.activeSetOverride = { ids ->
            activeIds.forEach { it.fill(0) }
            activeIds = ids.map(ByteArray::copyOf)
            true
        }
        assertTrue(KeyboxActivation.commitAndPublish(listOf(original)))
        assertEquals(1, activeIds.size)
        assertEquals(1, CertHack.getKeyboxCount())

        // Backend B starts with no process-local secret/CRL state while managed A handles still exist.
        NativeBackend.observeBackendIdentityForTesting(identityB)
        activeIds.forEach { it.fill(0) }
        activeIds = emptyList()
        assertTrue(NativeBackend.consumeBackendStateReset())
        assertThrows(RustBackendStateException::class.java) { original.keyPair().private.encoded }

        var recoveryCalls = 0
        var crlGeneration: CrlWire.Handle? = null
        var recovered: CertHack.KeyBox? = null
        BackendStateRecovery.recoveryOverride = { identity ->
            recoveryCalls++
            assertEquals(identityB, identity)
            crlGeneration = CrlWire.Handle(1, 1, 1)
            recovered = keyboxForCurrentIdentity("backend-b.xml")
            KeyboxActivation.commitAndPublish(listOf(requireNotNull(recovered)))
        }

        var attempts = 0
        CertificateBackend.rewriteOverride = { request ->
            assertArrayEquals(requireNotNull(recovered).keyPair().private.encoded, request.keyId)
            byteArrayOf(0x30, 0x01, 0x00)
        }
        val rewritten =
            BackendRecovery.withOneRetry {
                attempts++
                val selected = if (attempts == 1) original else requireNotNull(recovered)
                val keyId = selected.keyPair().private.encoded
                try {
                    CertificateBackend.rewrite(
                        genuineLeafDer = byteArrayOf(0x30, 0x00),
                        keyId = keyId,
                        signingAlgorithm = CertificateBackend.SIGNING_EC_P256_SHA256,
                        systemDisposition = CertificateBackend.PATCH_KEEP,
                        systemValue = 0,
                        vendorDisposition = CertificateBackend.PATCH_KEEP,
                        vendorValue = 0,
                        bootDisposition = CertificateBackend.PATCH_KEEP,
                        bootValue = 0,
                        idOverrides = emptyMap(),
                        moduleHash = null,
                        verifiedBootKey = ByteArray(32) { 0x11 },
                        verifiedBootHash = ByteArray(32) { 0x22 },
                    )
                } finally {
                    keyId.fill(0)
                }
            }

        assertArrayEquals(byteArrayOf(0x30, 0x01, 0x00), rewritten)
        assertEquals(2, attempts)
        assertEquals(1, recoveryCalls)
        assertEquals(1L, requireNotNull(crlGeneration).generation)
        assertEquals(1, activeIds.size)
        assertEquals(1, CertHack.getKeyboxCount())
        assertTrue(KeyboxActivation.isCommittedForCurrentInstance())
        activeIds.forEach { it.fill(0) }
    }

    @Test
    fun `second stale failure escapes without recursive recovery`() {
        NativeBackend.observeBackendIdentityForTesting(identityA)
        NativeBackend.observeBackendIdentityForTesting(identityB)
        NativeBackend.consumeBackendStateReset()
        var recoveryCalls = 0
        BackendRecovery.recoveryOverride = {
            recoveryCalls++
            true
        }
        var attempts = 0

        assertThrows(RustBackendStateException::class.java) {
            BackendRecovery.withOneRetry {
                attempts++
                throw RustBackendStateException(BackendStatus.STATE_RESET)
            }
        }
        assertEquals(2, attempts)
        assertEquals(1, recoveryCalls)
    }

    @Test
    fun `concurrent recovery attempt fails fast while single flight owns coordinator`() {
        NativeBackend.observeBackendIdentityForTesting(identityA)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstResult = AtomicReference<Boolean>()
        BackendStateRecovery.recoveryOverride = {
            firstEntered.countDown()
            assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
            true
        }

        val first =
            Thread {
                firstResult.set(BackendStateRecovery.recover(identityA))
            }
        first.start()
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val secondResult = AtomicReference<Boolean>()
        val secondFinished = CountDownLatch(1)
        val second =
            Thread {
                try {
                    secondResult.set(BackendStateRecovery.recover(identityA))
                } finally {
                    secondFinished.countDown()
                }
            }
        second.start()
        try {
            assertTrue(
                "overlapping recovery must not wait on the active single flight",
                secondFinished.await(2, TimeUnit.SECONDS),
            )
            assertFalse(requireNotNull(secondResult.get()))
        } finally {
            releaseFirst.countDown()
        }

        first.join(2_000)
        second.join(2_000)
        assertTrue(requireNotNull(firstResult.get()))
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
    }

    private fun keyboxForCurrentIdentity(filename: String): CertHack.KeyBox {
        val fixture =
            ManagedOpaqueKeyOracle.parse(
                TestKeyboxFixtures.validEcKeyboxXml.reader(),
                filename,
            ).single()
        val keyId = fixture.keyPair().private.encoded
        return try {
            CertHack.KeyBox(
                KeyPair(
                    fixture.keyPair().public,
                    BackendKeyHandle(fixture.keyPair().private.algorithm, keyId),
                ),
                fixture.certificates(),
                filename,
            )
        } finally {
            keyId.fill(0)
        }
    }
}
