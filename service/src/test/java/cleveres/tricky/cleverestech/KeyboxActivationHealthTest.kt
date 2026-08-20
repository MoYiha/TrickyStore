package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxActivationHealthTest {
    @After
    fun tearDown() {
        KeyboxActivation.resetForTesting()
        KeyboxLoader.resetForTesting()
    }

    @Test
    fun `failed active-set commit makes managed keybox snapshot unreadable until recovery`() {
        KeyboxLoader.activeSetOverride = { false }

        assertFalse(KeyboxLoader.commitActive(emptyList()))
        assertFalse(KeyboxLoader.isActiveSetHealthy())
        assertThrows(IllegalStateException::class.java) {
            CertHack.getKeyboxCount()
        }

        KeyboxLoader.activeSetOverride = { true }
        assertTrue(KeyboxLoader.commitActive(emptyList()))
        assertTrue(KeyboxLoader.isActiveSetHealthy())
        CertHack.getKeyboxCount()
    }

    @Test
    fun `older refresh cannot commit after a newer generation starts`() {
        val backendCommits = AtomicInteger()
        KeyboxLoader.activeSetOverride = {
            backendCommits.incrementAndGet()
            true
        }

        val older = KeyboxActivation.beginRefresh()
        val newer = KeyboxActivation.beginRefresh()

        assertEquals(
            KeyboxActivation.PublicationResult.SUPERSEDED,
            KeyboxActivation.commitAndPublish(older, emptyList()),
        )
        assertEquals(0, backendCommits.get())

        assertEquals(
            KeyboxActivation.PublicationResult.COMMITTED,
            KeyboxActivation.commitAndPublish(newer, emptyList()),
        )
        assertEquals(1, backendCommits.get())
    }

    @Test
    fun `publication waits until active snapshot readers finish`() {
        val backendCommits = AtomicInteger()
        val attempting = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val result = AtomicReference<KeyboxActivation.PublicationResult>()
        KeyboxLoader.activeSetOverride = {
            backendCommits.incrementAndGet()
            true
        }
        val ticket = KeyboxActivation.beginRefresh()

        KeyboxActivation.lockPublishedSnapshot()
        val worker =
            Thread {
                attempting.countDown()
                try {
                    result.set(KeyboxActivation.commitAndPublish(ticket, emptyList()))
                } finally {
                    finished.countDown()
                }
            }
        worker.start()
        try {
            assertTrue(attempting.await(2, TimeUnit.SECONDS))
            assertFalse(finished.await(100, TimeUnit.MILLISECONDS))
            assertEquals(0, backendCommits.get())
        } finally {
            KeyboxActivation.unlockPublishedSnapshot()
        }

        assertTrue(finished.await(2, TimeUnit.SECONDS))
        worker.join(2_000)
        assertFalse(worker.isAlive)
        assertEquals(KeyboxActivation.PublicationResult.COMMITTED, result.get())
        assertEquals(1, backendCommits.get())
    }

    @Test
    fun `publication recovery never blocks in reverse refresh lock order`() {
        val refreshEntered = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        val holder =
            Thread {
                KeyboxActivation.coordinateRefresh {
                    refreshEntered.countDown()
                    releaseRefresh.await(2, TimeUnit.SECONDS)
                }
            }
        holder.start()
        assertTrue(refreshEntered.await(2, TimeUnit.SECONDS))

        KeyboxActivation.lockPublishedSnapshot()
        try {
            assertThrows(IllegalStateException::class.java) {
                KeyboxActivation.coordinateRefresh {
                    throw AssertionError("busy reverse-order recovery must not enter refresh work")
                }
            }
        } finally {
            KeyboxActivation.unlockPublishedSnapshot()
            releaseRefresh.countDown()
        }

        holder.join(2_000)
        assertFalse(holder.isAlive)
    }
}
