package cleveres.tricky.cleverestech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DrmPrivacyIdentityTest {
    private val identityComponents =
        listOf(
            "351234567890123",
            "351234567890131",
            "310260123456789",
            "8901120200000000003",
            "A1B2C3D4E5F607",
            "+12025550123",
            "SERIAL123456",
        )

    @Test
    fun `same app identity produces a stable DRM pseudonym`() {
        val first = DrmPrivacyIdentity.derive(10_123, 32, identityComponents)
        val second = DrmPrivacyIdentity.derive(10_123, 32, identityComponents)

        assertArrayEquals(first, second)
        assertEquals(32, first.size)
    }

    @Test
    fun `different application UIDs produce different DRM pseudonyms`() {
        val first = DrmPrivacyIdentity.derive(10_123, 32, identityComponents)
        val second = DrmPrivacyIdentity.derive(10_124, 32, identityComponents)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `different isolated identities produce different DRM pseudonyms`() {
        val first = DrmPrivacyIdentity.derive(10_123, 32, identityComponents)
        val changed = identityComponents.toMutableList().apply { this[lastIndex] = "SERIAL654321" }
        val second = DrmPrivacyIdentity.derive(10_123, 32, changed)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `DRM pseudonym preserves supported vendor identifier lengths`() {
        for (length in listOf(8, 16, 32, 64)) {
            assertEquals(length, DrmPrivacyIdentity.derive(10_123, length, identityComponents).size)
        }
    }

    @Test
    fun `invalid DRM identity inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DrmPrivacyIdentity.derive(9999, 32, identityComponents)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrmPrivacyIdentity.derive(10_123, 7, identityComponents)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrmPrivacyIdentity.derive(10_123, 65, identityComponents)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrmPrivacyIdentity.derive(10_123, 32, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            DrmPrivacyIdentity.derive(10_123, 32, listOf(""))
        }
    }
}
