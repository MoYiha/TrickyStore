package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import java.io.File
import java.security.KeyPair
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigKeyboxActivationTest {
    @Test
    fun `mixed validity keybox pool is rejected as a unit and commits empty active set`() {
        withKeyboxRoot { root ->
            val keyboxDir = File(root, "keyboxes").also { check(it.mkdirs()) }
            File(keyboxDir, "valid.xml").writeText(TestKeyboxFixtures.validEcKeyboxXml)
            File(keyboxDir, "revoked.xml").writeText(TestKeyboxFixtures.validEcKeyboxXml)

            ManagedKeyboxParserOracle.install()
            val verificationCalls = AtomicInteger()
            val committedSizes = ArrayList<Int>()
            KeyboxLoader.activeSetOverride = { ids ->
                committedSizes += ids.size
                ids.all(ManagedOpaqueKeyOracle::contains)
            }
            Config.updateKeyBoxesSync(emptySet()) { _, _ ->
                if (verificationCalls.getAndIncrement() == 0) {
                    KeyboxVerifier.Status.VALID
                } else {
                    KeyboxVerifier.Status.REVOKED
                }
            }

            assertEquals(2, verificationCalls.get())
            assertEquals(listOf(0), committedSizes)
            assertEquals(0, CertHack.getKeyboxCount())
        }
    }

    @Test
    fun `managed keyboxes are never published before backend commit succeeds`() {
        withKeyboxRoot { root ->
            val keyboxDir = File(root, "keyboxes").also { check(it.mkdirs()) }
            File(keyboxDir, "valid.xml").writeText(TestKeyboxFixtures.validEcKeyboxXml)
            ManagedKeyboxParserOracle.install()

            var commits = 0
            KeyboxLoader.activeSetOverride = { ids ->
                commits++
                assertEquals("managed state was published before backend commit", 0, CertHack.getKeyboxCount())
                assertEquals(1, ids.size)
                false
            }
            Config.updateKeyBoxesSync(emptySet()) { _, _ -> KeyboxVerifier.Status.VALID }

            assertEquals(1, commits)
            assertThrows(IllegalStateException::class.java) {
                CertHack.getKeyboxCount()
            }
        }
    }

    @Test
    fun `deleted active keybox commits empty set and removes managed snapshot`() {
        withKeyboxRoot { root ->
            val keyboxDir = File(root, "keyboxes").also { check(it.mkdirs()) }
            val file = File(keyboxDir, "active.xml")
            file.writeText(TestKeyboxFixtures.validEcKeyboxXml)
            ManagedKeyboxParserOracle.install()

            val committedSizes = ArrayList<Int>()
            KeyboxLoader.activeSetOverride = { ids ->
                committedSizes += ids.size
                ids.all(ManagedOpaqueKeyOracle::contains)
            }
            Config.updateKeyBoxesSync(emptySet()) { _, _ -> KeyboxVerifier.Status.VALID }
            assertEquals(1, CertHack.getKeyboxCount())

            assertTrue(file.delete())
            Config.updateKeyBoxesSync(emptySet()) { _, _ -> KeyboxVerifier.Status.VALID }

            assertEquals(listOf(1, 0), committedSizes)
            assertEquals(0, CertHack.getKeyboxCount())
        }
    }

    @Test
    fun `more than backend capacity transient rejected candidates cannot starve later activation`() {
        withKeyboxRoot { root ->
            val keyboxDir = File(root, "keyboxes").also { check(it.mkdirs()) }
            val file = File(keyboxDir, "candidate.xml")
            file.writeText("seed")

            val fixture = ManagedOpaqueKeyOracle.parse(TestKeyboxFixtures.validEcKeyboxXml.reader(), "fixture.xml").single()
            val candidateStore = LinkedHashSet<String>()
            var sequence = 1
            fun candidate(idNumber: Int): CertHack.KeyBox {
                val id = ByteArray(KEY_ID_BYTES)
                id[0] = 1
                id[12] = (idNumber ushr 24).toByte()
                id[13] = (idNumber ushr 16).toByte()
                id[14] = (idNumber ushr 8).toByte()
                id[15] = idNumber.toByte()
                val encoded = id.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!candidateStore.add(encoded) || candidateStore.size > MAX_STORED_KEYS) {
                    throw IllegalStateException("simulated Rust key store exhausted")
                }
                return CertHack.KeyBox(
                    KeyPair(fixture.keyPair().public, BackendKeyHandle(fixture.keyPair().private.algorithm, id)),
                    fixture.certificates(),
                    "candidate.xml",
                )
            }

            KeyboxLoader.fileParserOverride = { _, _ -> listOf(candidate(sequence++)) }
            KeyboxLoader.activeSetOverride = { ids ->
                val retained = ids.map { it.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) } }.toSet()
                candidateStore.retainAll(retained)
                true
            }

            repeat(MAX_STORED_KEYS + 1) { cycle ->
                file.writeText("rejected-$cycle-${"x".repeat(cycle % 7)}")
                file.setLastModified(System.currentTimeMillis() + cycle + 1L)
                Config.updateKeyBoxesSync(emptySet()) { _, _ -> KeyboxVerifier.Status.REVOKED }
                assertEquals(0, CertHack.getKeyboxCount())
                assertTrue("transient candidates were not pruned", candidateStore.isEmpty())
            }

            file.writeText("legitimate-final")
            file.setLastModified(System.currentTimeMillis() + MAX_STORED_KEYS + 1000L)
            Config.updateKeyBoxesSync(emptySet()) { _, _ -> KeyboxVerifier.Status.VALID }

            assertEquals(1, CertHack.getKeyboxCount())
            assertEquals(1, candidateStore.size)
        }
    }

    private fun withKeyboxRoot(block: (File) -> Unit) {
        val originalRoot = Config.getConfigRoot()
        val root = File.createTempFile("keybox-activation", ".tmp").also { it.delete() }
        check(root.mkdirs())
        root.deleteOnExit()
        try {
            Config.reset()
            Config.setRootForTesting(root)
            block(root)
        } finally {
            Config.reset()
            ManagedKeyboxParserOracle.reset()
            Config.setRootForTesting(originalRoot)
            ManagedOpaqueKeyOracle.readFromXml(null)
            root.deleteRecursively()
        }
    }

    private companion object {
        const val KEY_ID_BYTES = 16
        const val MAX_STORED_KEYS = 256
    }
}
