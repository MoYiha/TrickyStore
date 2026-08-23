package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PolicyCompatibilityRecoveryTest {
    private lateinit var root: File
    private lateinit var originalSecureFileImpl: SecureFileOperations

    @Before
    fun setUp() {
        Config.reset()
        root = Files.createTempDirectory("policy-compatibility-recovery-test").toFile()
        Config.setRootForTesting(root)
        originalSecureFileImpl = SecureFile.impl
        SecureFile.impl =
            object : SecureFileOperations {
                override fun writeText(
                    file: File,
                    content: String,
                ) {
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                }

                override fun mkdirs(
                    file: File,
                    mode: Int,
                ) {
                    file.mkdirs()
                }

                override fun touch(
                    file: File,
                    mode: Int,
                ) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
            }
    }

    @After
    fun tearDown() {
        SecureFile.impl = originalSecureFileImpl
        root.deleteRecursively()
        Config.reset()
    }

    @Test
    fun `bootstrap snapshot is not ready for policy traffic`() {
        assertFalse(PolicyApi.policyRuntimeReady(JSONObject().put("recovery", "bootstrap")))
        assertFalse(PolicyApi.policyRuntimeReady(JSONObject().put("recovery", "BOOTSTRAP")))
        assertTrue(PolicyApi.policyRuntimeReady(JSONObject().put("recovery", "legacy")))
        assertTrue(PolicyApi.policyRuntimeReady(JSONObject().put("recovery", "configured")))
        assertTrue(PolicyApi.policyRuntimeReady(JSONObject().put("recovery", "last_known_good")))
    }

    @Test
    fun `stale compatibility remains pending until explicit retry heals markers`() {
        val state = policy(build = true)

        val before = PolicyApi.compatibilityStatusResponse(state, root)
        assertEquals("pending", before.getString("compatibilitySync"))
        assertTrue(before.getString("compatibilityWarning").contains("Retry before reboot"))
        assertFalse(File(root, LegacyIdentityMarkers.ENGINE).exists())
        assertFalse(File(root, LegacyIdentityMarkers.BUILD).exists())

        val retry = PolicyApi.retryCompatibility(state, root)
        assertEquals(CompatibilitySyncStatus.OK, retry.compatibilitySync)
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.BUILD).isFile)

        val after = PolicyApi.compatibilityStatusResponse(state, root)
        assertEquals("ok", after.getString("compatibilitySync"))
        assertFalse(after.has("compatibilityWarning"))
    }

    @Test
    fun `policy read self heals stale compatibility without changing canonical state`() {
        val state = policy(build = true).put("generation", 41)

        val response = PolicyApi.reconciledCompatibilityResponse(state, root)

        assertEquals("ok", response.getString("compatibilitySync"))
        assertEquals(41, response.getInt("generation"))
        assertTrue(response.getJSONObject("features").getBoolean("buildIdentity"))
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.BUILD).isFile)
        assertFalse(response.has("compatibilityWarning"))
    }

    @Test
    fun `policy read keeps pending warning when compatibility cannot be safely healed`() {
        val state = policy(build = true)
        File(root, LegacyIdentityMarkers.BUILD).mkdirs()

        val response = PolicyApi.reconciledCompatibilityResponse(state, root)

        assertEquals("pending", response.getString("compatibilitySync"))
        assertTrue(response.getString("compatibilityWarning").contains("Retry before reboot"))
        assertTrue(File(root, LegacyIdentityMarkers.BUILD).isDirectory)
    }

    @Test
    fun `legacy state never claims ownership of compatibility markers`() {
        File(root, LegacyIdentityMarkers.ENGINE).writeText("")
        val state = policy(build = false).put("source", "legacy")

        val status = PolicyApi.compatibilityStatusResponse(state, root)
        val retry = PolicyApi.retryCompatibility(state, root)
        val reconciled = PolicyApi.reconciledCompatibilityResponse(state, root)

        assertEquals("ok", status.getString("compatibilitySync"))
        assertEquals(CompatibilitySyncStatus.OK, retry.compatibilitySync)
        assertEquals("ok", reconciled.getString("compatibilitySync"))
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
    }

    private fun policy(build: Boolean): JSONObject =
        JSONObject()
            .put("source", "v2")
            .put(
                "features",
                JSONObject()
                    .put("buildIdentity", build)
                    .put("attestationIdentity", false)
                    .put("telephonyIdentity", false)
                    .put("regionIdentity", false)
                    .put("identityRefresh", false)
                    .put("securityPatch", false),
            )
            .put("profiles", JSONArray())
            .put("activeProfile", JSONObject.NULL)
}
