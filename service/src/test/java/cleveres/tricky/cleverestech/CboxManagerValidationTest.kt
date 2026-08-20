package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CboxManagerValidationTest {
    @Test
    fun `legacy empty password remains decryptable`() {
        assertTrue(CboxManager.isUnlockPasswordWithinLimit(""))
    }

    @Test
    fun `password over backend UTF-16 limit is rejected`() {
        assertFalse(CboxManager.isUnlockPasswordWithinLimit("a".repeat(1025)))
    }
}
