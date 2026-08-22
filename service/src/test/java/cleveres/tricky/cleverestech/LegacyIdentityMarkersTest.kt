package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class LegacyIdentityMarkersTest {
    private lateinit var root: File
    private lateinit var originalSecureFileImpl: SecureFileOperations

    @Before
    fun setUp() {
        Config.reset()
        root = Files.createTempDirectory("identity-markers-test").toFile()
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
    fun `v2 identity features synchronize early boot markers`() {
        val enabled = policy(build = true, attestation = true, refresh = true)

        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, enabled).isSuccess)
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.BUILD).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.REFRESH).isFile)
        assertFalse(File(root, LegacyIdentityMarkers.TELEPHONY).exists())
        assertFalse(File(root, LegacyIdentityMarkers.REGION).exists())

        val patchOnly = policy(patch = true)
        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, patchOnly).isSuccess)
        assertFalse(File(root, LegacyIdentityMarkers.ENGINE).exists())
        assertFalse(File(root, LegacyIdentityMarkers.BUILD).exists())
        assertFalse(File(root, LegacyIdentityMarkers.REFRESH).exists())
    }

    @Test
    fun `active profile feature overrides participate in global boot state`() {
        val profile =
            JSONObject()
                .put("name", "Boot Profile")
                .put("features", JSONObject().put("buildIdentity", true).put("regionIdentity", true))
        val state = policy().put("profiles", JSONArray().put(profile)).put("activeProfile", "Boot Profile")

        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, state).isSuccess)
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.BUILD).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.REGION).isFile)
        assertFalse(File(root, LegacyIdentityMarkers.TELEPHONY).exists())
    }

    @Test
    fun `json null active profile never aliases sentinel like profile names`() {
        listOf("null", "true", "false", "0", "undefined").forEach { profileName ->
            val profile =
                JSONObject()
                    .put("name", profileName)
                    .put("features", JSONObject().put("buildIdentity", true).put("regionIdentity", true))
            val state =
                policy()
                    .put("profiles", JSONArray().put(profile))
                    .put("activeProfile", JSONObject.NULL)

            assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, state).isSuccess)
            assertNoIdentityMarkers("JSON null must not select profile '$profileName'")
        }
    }

    @Test
    fun `literal null profile name activates only when explicitly selected as a string`() {
        val profile =
            JSONObject()
                .put("name", "null")
                .put("features", JSONObject().put("buildIdentity", true).put("regionIdentity", true))
        val state =
            policy()
                .put("profiles", JSONArray().put(profile))
                .put("activeProfile", "null")

        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, state).isSuccess)
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.BUILD).isFile)
        assertTrue(File(root, LegacyIdentityMarkers.REGION).isFile)
        assertFalse(File(root, LegacyIdentityMarkers.TELEPHONY).exists())
        assertFalse(File(root, LegacyIdentityMarkers.REFRESH).exists())
    }

    @Test
    fun `legacy source remains marker source of truth`() {
        File(root, LegacyIdentityMarkers.ENGINE).writeText("")
        val state = policy().put("source", "legacy")

        assertTrue(LegacyIdentityMarkers.syncFromPolicyState(root, state).isSuccess)
        assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
    }

    @Test
    fun `preflight rejects symbolic link compatibility marker`() {
        val outside = Files.createTempFile("identity-marker-outside", ".txt")
        outside.toFile().writeText("unchanged")
        val marker = File(root, LegacyIdentityMarkers.ENGINE).toPath()
        try {
            Files.createSymbolicLink(marker, outside)
        } catch (_: UnsupportedOperationException) {
            Files.deleteIfExists(outside)
            return
        }

        val result = runCatching { LegacyIdentityMarkers.preflight(root) }

        assertTrue(result.isFailure)
        assertTrue(Files.isSymbolicLink(marker))
        assertTrue(outside.toFile().readText() == "unchanged")
        Files.deleteIfExists(outside)
    }

    @Test
    fun `touch that creates marker then throws is rolled back`() {
        val marker = File(root, LegacyIdentityMarkers.BUILD)
        SecureFile.impl =
            object : SecureFileOperations {
                override fun writeText(
                    file: File,
                    content: String,
                ) = Unit

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
                    throw IOException("chmod failed after create")
                }
            }

        val result =
            runCatching {
                LegacyIdentityMarkers.apply(
                    listOf(LegacyIdentityMarkers.Operation(LegacyIdentityMarkers.BUILD, marker, true)),
                )
            }

        assertTrue(result.isFailure)
        assertFalse("partially created marker must be removed", marker.exists())
    }

    private fun assertNoIdentityMarkers(message: String) {
        listOf(
            LegacyIdentityMarkers.ENGINE,
            LegacyIdentityMarkers.BUILD,
            LegacyIdentityMarkers.TELEPHONY,
            LegacyIdentityMarkers.REGION,
            LegacyIdentityMarkers.REFRESH,
        ).forEach { marker ->
            assertFalse("$message: unexpected $marker", File(root, marker).exists())
        }
    }

    private fun policy(
        build: Boolean = false,
        attestation: Boolean = false,
        telephony: Boolean = false,
        region: Boolean = false,
        refresh: Boolean = false,
        patch: Boolean = false,
    ): JSONObject =
        JSONObject()
            .put("source", "v2")
            .put(
                "features",
                JSONObject()
                    .put("buildIdentity", build)
                    .put("attestationIdentity", attestation)
                    .put("telephonyIdentity", telephony)
                    .put("regionIdentity", region)
                    .put("identityRefresh", refresh)
                    .put("securityPatch", patch),
            )
            .put("profiles", JSONArray())
            .put("activeProfile", JSONObject.NULL)
}
