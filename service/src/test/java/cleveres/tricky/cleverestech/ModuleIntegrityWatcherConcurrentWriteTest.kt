package cleveres.tricky.cleverestech

import android.os.FileObserver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ModuleIntegrityWatcherConcurrentWriteTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val violations = CopyOnWriteArrayList<List<String>>()
    private val testManifest =
        ParsedManifest(
            version = 1,
            files =
                listOf(
                    ManifestFileEntry("test.so", "a".repeat(64), "regular"),
                    ManifestFileEntry("inject", "b".repeat(64), "executable"),
                ),
            signature = "c".repeat(64),
        )

    @Before
    fun setUp() {
        violations.clear()
        Config.setRootForTesting(tempFolder.root)
        ModuleIntegrityVerifier.resetForTesting()
        ModuleIntegrityWatcher.resetForTesting()
        ModuleIntegrityWatcher.fullVerificationDelayMs = 25L
        ModuleIntegrityWatcher.fullVerifier = { IntegrityResult.Pass }
    }

    @After
    fun tearDown() {
        ModuleIntegrityWatcher.resetForTesting()
        ModuleIntegrityVerifier.resetForTesting()
        Config.reset()
    }

    @Test
    fun laterPendingWriteGetsItsOwnGraceBeforeFullVerification() {
        val dir = tempFolder.newFolder("staggered-writes", "cleverestricky")
        val clock = AtomicLong(0L)
        ModuleIntegrityWatcher.nanoTime = clock::get
        ModuleIntegrityWatcher.start(dir, testManifest) { violations.add(it) }

        ModuleIntegrityWatcher.injectChildEventForTesting(FileObserver.MODIFY, "test.so")
        clock.set(TimeUnit.SECONDS.toNanos(4))
        ModuleIntegrityWatcher.injectChildEventForTesting(FileObserver.MODIFY, "inject")
        clock.set(TimeUnit.SECONDS.toNanos(5))

        Thread.sleep(150)
        assertEquals(
            "An older expired write must not consume a newer write's grace period",
            0,
            ModuleIntegrityWatcher.fullVerificationExecutions.get(),
        )
        assertEquals(2, ModuleIntegrityWatcher.pendingWriteCountForTesting())
        assertTrue(violations.isEmpty())

        clock.set(TimeUnit.SECONDS.toNanos(9))
        awaitCondition { ModuleIntegrityWatcher.pendingWriteCountForTesting() == 0 }
        assertEquals(1, ModuleIntegrityWatcher.fullVerificationExecutions.get())
        assertTrue(violations.isEmpty())
    }

    @Test
    fun laterOverflowedWriteRefreshesTheConservativeGraceDeadline() {
        val dir = tempFolder.newFolder("staggered-overflow", "cleverestricky")
        val clock = AtomicLong(0L)
        ModuleIntegrityWatcher.nanoTime = clock::get
        ModuleIntegrityWatcher.start(dir, testManifest) { violations.add(it) }

        repeat(65) {
            ModuleIntegrityWatcher.injectChildEventForTesting(FileObserver.CREATE, "payload$it.so")
        }
        assertEquals(64, ModuleIntegrityWatcher.pendingWriteCountForTesting())

        clock.set(TimeUnit.SECONDS.toNanos(4))
        ModuleIntegrityWatcher.injectChildEventForTesting(FileObserver.CREATE, "payload65.so")
        clock.set(TimeUnit.SECONDS.toNanos(5))

        Thread.sleep(150)
        assertEquals(
            "Fresh overflow activity must keep full verification behind its own grace period",
            0,
            ModuleIntegrityWatcher.fullVerificationExecutions.get(),
        )
        assertEquals(64, ModuleIntegrityWatcher.pendingWriteCountForTesting())
        assertTrue(violations.isEmpty())

        clock.set(TimeUnit.SECONDS.toNanos(9))
        awaitCondition { ModuleIntegrityWatcher.pendingWriteCountForTesting() == 0 }
        assertEquals(1, ModuleIntegrityWatcher.fullVerificationExecutions.get())
        assertTrue(violations.isEmpty())
    }

    private fun awaitCondition(
        timeoutMs: Long = 3_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("Timed out waiting for integrity watcher condition", condition())
    }
}
