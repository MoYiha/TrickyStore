package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigKeyboxActivationTest {
    @Test
    fun `arbitrary direct root XML is activated like legacy keybox xml`() {
        withKeyboxRoot { root ->
            File(root, "A1B2C3.xml").writeText(TestKeyboxFixtures.validEcKeyboxXml)
            ManagedKeyboxParserOracle.install()
            KeyboxLoader.activeSetOverride = { ids -> ids.size == 1 && ids.all(ManagedOpaqueKeyOracle::contains) }
            assertTrue(Config.updateKeyBoxesSync(emptySet()) { _, _ -> KeyboxVerifier.Status.VALID })
            assertEquals(1, CertHack.getKeyboxSourceCount())
        }
    }

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
    fun `more than backend capacity rejected refreshes commit empty active set every cycle`() {
        withKeyboxRoot { root ->
            val keyboxDir = File(root, "keyboxes").also { check(it.mkdirs()) }
            File(keyboxDir, "candidate.xml").writeText(TestKeyboxFixtures.validEcKeyboxXml)
            ManagedKeyboxParserOracle.install()

            val committedSizes = ArrayList<Int>()
            KeyboxLoader.activeSetOverride = { ids ->
                committedSizes += ids.size
                true
            }

            repeat(MAX_STORED_KEYS + 1) {
                Config.updateKeyBoxesSync(emptySet()) { _, _ -> KeyboxVerifier.Status.REVOKED }
                assertEquals(0, CertHack.getKeyboxCount())
            }

            assertEquals(MAX_STORED_KEYS + 1, committedSizes.size)
            assertTrue("every rejected refresh must prune staged keys", committedSizes.all { it == 0 })
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
        const val MAX_STORED_KEYS = 256
    }
}
