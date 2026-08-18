package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxActivationHealthTest {
    @After
    fun tearDown() {
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
}
