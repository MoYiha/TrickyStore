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

class IdentityPolicyContractTest {
    private lateinit var root: File
    private lateinit var originalSecureFileImpl: SecureFileOperations

    @Before
    fun setUp() {
        Config.reset()
        root = Files.createTempDirectory("identity-policy-contract-test").toFile()
        Config.setRootForTesting(root)
        PolicyState.setRootForTesting(root)
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
        PolicyState.resetForTesting()
        root.deleteRecursively()
        Config.reset()
    }

    @Test
    fun `canonical json null stays distinct from literal null profile through boot markers`() {
        val configured = PolicyState.replaceFromJson(policyWithNullNamedProfile().toString()).getOrThrow()

        assertTrue("canonical absence must remain JSON null", configured.isNull("activeProfile"))
        assertFalse(PolicyState.isFeatureEnabled(PolicyState.Feature.BUILD_IDENTITY))
        assertFalse(PolicyState.isFeatureEnabled(PolicyState.Feature.REGION_IDENTITY))
        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, configured).isSuccess)
        assertNoIdentityMarkers()

        val activated =
            PolicyState.profileAction(
                "activate",
                JSONObject().put("name", "null"),
            ).getOrThrow()

        assertEquals("null", activated.getString("activeProfile"))
        assertTrue(PolicyState.isFeatureEnabled(PolicyState.Feature.BUILD_IDENTITY))
        assertTrue(PolicyState.isFeatureEnabled(PolicyState.Feature.REGION_IDENTITY))
        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, activated).isSuccess)
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.BUILD).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.REGION).isFile)

        val deactivated = PolicyState.profileAction("deactivate", JSONObject()).getOrThrow()

        assertTrue("deactivation must serialize back to JSON null", deactivated.isNull("activeProfile"))
        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, deactivated).isSuccess)
        assertNoIdentityMarkers()
    }

    private fun assertNoIdentityMarkers() {
        listOf(
            LegacyIdentityMarkers.ENGINE,
            LegacyIdentityMarkers.BUILD,
            LegacyIdentityMarkers.TELEPHONY,
            LegacyIdentityMarkers.REGION,
            LegacyIdentityMarkers.REFRESH,
        ).forEach { marker ->
            assertFalse("unexpected compatibility marker: $marker", File(root, marker).exists())
        }
    }

    private fun policyWithNullNamedProfile(): JSONObject {
        val features =
            JSONObject()
                .put("buildIdentity", false)
                .put("attestationIdentity", false)
                .put("telephonyIdentity", false)
                .put("regionIdentity", false)
                .put("identityRefresh", false)
                .put("securityPatch", false)
        val patch = JSONObject().put("mode", "device_default")
        val profile =
            JSONObject()
                .put("name", "null")
                .put("applications", JSONArray())
                .put("privacy", "inherit")
                .put("features", JSONObject().put("buildIdentity", true).put("regionIdentity", true))
                .put("securityPatch", JSONObject())

        return JSONObject()
            .put("version", PolicyState.SCHEMA_VERSION)
            .put("features", features)
            .put(
                "securityPatch",
                JSONObject()
                    .put("automaticThresholdMonths", 6)
                    .put("system", JSONObject(patch.toString()))
                    .put("vendor", JSONObject(patch.toString()))
                    .put("boot", JSONObject(patch.toString())),
            )
            .put("profiles", JSONArray().put(profile))
            .put("activeProfile", JSONObject.NULL)
    }
}
