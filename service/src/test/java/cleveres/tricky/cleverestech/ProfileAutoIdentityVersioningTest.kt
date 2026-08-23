package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProfileAutoIdentityVersioningTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        Config.reset()
        root = Files.createTempDirectory("profile-auto-version-test").toFile()
        Config.setRootForTesting(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        Config.reset()
    }

    @Test
    fun `successful saves advance persisted generation`() {
        assertTrue(ProfileAutoIdentityStore.save(root, result("one"), nowMs = 10).isSuccess)
        assertEquals(1, ProfileAutoIdentityStore.generation())
        assertTrue(ProfileAutoIdentityStore.save(root, result("two"), nowMs = 20).isSuccess)
        assertEquals(2, ProfileAutoIdentityStore.generation())
        assertEquals(20, ProfileAutoIdentityStore.updatedAtMs())

        ProfileAutoIdentityStore.resetForTesting()
        assertTrue(ProfileAutoIdentityStore.load(root).isSuccess)
        assertEquals(2, ProfileAutoIdentityStore.generation())
        assertEquals("Pixel two", ProfileAutoIdentityStore.get("MODEL"))
    }

    @Test
    fun `legacy unversioned snapshot loads and upgrades on next save`() {
        File(root, ProfileAutoIdentityStore.FILE_NAME).writeText(
            """
            BRAND=google
            DEVICE=tokay
            PRODUCT=tokay_beta
            MANUFACTURER=Google
            MODEL=Pixel Legacy
            FINGERPRINT=google/tokay_beta/tokay:17/LEGACY/1:user/release-keys
            RELEASE=17
            BUILD_ID=LEGACY
            INCREMENTAL=1
            TYPE=user
            TAGS=release-keys
            SECURITY_PATCH=2026-08-05
            """.trimIndent() + "\n",
        )
        assertTrue(ProfileAutoIdentityStore.load(root).isSuccess)
        assertEquals(1, ProfileAutoIdentityStore.generation())
        assertEquals(0, ProfileAutoIdentityStore.updatedAtMs())

        assertTrue(ProfileAutoIdentityStore.save(root, result("new"), nowMs = 30).isSuccess)
        val text = File(root, ProfileAutoIdentityStore.FILE_NAME).readText()
        assertTrue(text.startsWith("version=2\ngeneration=2\nupdated_at_ms=30\n"))
    }

    private fun result(suffix: String): AutoIdentityManager.Result =
        AutoIdentityManager.Result(
            model = "Pixel $suffix",
            product = "tokay_beta",
            device = "tokay",
            fingerprint = "google/tokay_beta/tokay:17/BP31.260801.001/$suffix:user/release-keys",
            buildId = "BP31.260801.001",
            incremental = suffix,
            release = "17",
            securityPatch = "2026-08-05",
            securityPatchEstimated = false,
        )
}
