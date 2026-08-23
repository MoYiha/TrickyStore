package cleveres.tricky.cleverestech

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BootPolicyProjectionTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("boot-policy-projection-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `projection persists only top level boot features`() {
        val state =
            JSONObject()
                .put(
                    "features",
                    JSONObject()
                        .put("buildIdentity", false)
                        .put("regionIdentity", true)
                        .put("identityRefresh", false),
                ).put(
                    "profiles",
                    JSONArray().put(
                        JSONObject()
                            .put("name", "Active")
                            .put("enabled", true)
                            .put("features", JSONObject().put("buildIdentity", true).put("identityRefresh", true)),
                    ),
                ).put("activeProfile", "Active")

        BootPolicyProjection.write(root, state)

        assertEquals(
            BootPolicyProjection.State(buildIdentity = false, regionIdentity = true, identityRefresh = false),
            BootPolicyProjection.read(root),
        )
        val text = File(root, BootPolicyProjection.FILE_NAME).readText()
        assertEquals("version=1\nbuild=0\nregion=1\nrefresh=0\n", text)
        assertFalse(text.contains("Active"))
    }

    @Test
    fun `malformed or linked projection fails closed`() {
        val file = File(root, BootPolicyProjection.FILE_NAME)
        file.writeText("version=1\nbuild=2\nregion=0\nrefresh=0\n")
        assertNull(BootPolicyProjection.read(root))

        file.delete()
        val outside = File(root.parentFile, "projection-${System.nanoTime()}")
        outside.writeText("version=1\nbuild=1\nregion=0\nrefresh=0\n")
        try {
            Files.createSymbolicLink(file.toPath(), outside.toPath())
        } catch (_: UnsupportedOperationException) {
            outside.delete()
            return
        }
        assertTrue(runCatching { BootPolicyProjection.read(root) }.isFailure)
        outside.delete()
    }

    @Test
    fun `synchronization detects stale projection`() {
        val state = JSONObject().put("features", JSONObject().put("buildIdentity", true))
        BootPolicyProjection.write(root, state)
        assertTrue(BootPolicyProjection.isSynchronized(root, state))
        assertFalse(
            BootPolicyProjection.isSynchronized(
                root,
                JSONObject().put("features", JSONObject().put("buildIdentity", false)),
            ),
        )
    }
}
