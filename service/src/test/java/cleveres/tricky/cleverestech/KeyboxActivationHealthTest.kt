package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import java.util.concurrent.atomic.AtomicInteger
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
}
