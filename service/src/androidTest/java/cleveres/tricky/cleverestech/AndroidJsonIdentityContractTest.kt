package cleveres.tricky.cleverestech

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class AndroidJsonIdentityContractTest {
    @Test
    fun `platform oracle runs on Android 17 API 37`() {
        assertEquals("Android platform contract must run on API 37", 37, Build.VERSION.SDK_INT)
        assertTrue(
            "Android platform contract must run on Android 17",
            Build.VERSION.RELEASE.startsWith("17"),
        )
    }

    @Test
    fun `platform JSON null cannot alias literal null profile name`() {
        val profile =
            JSONObject()
                .put("name", "null")
                .put("features", JSONObject().put("buildIdentity", true).put("regionIdentity", true))
        val state = policyState(profile, JSONObject.NULL)

        // This is Android platform behavior that host org.json implementations have
        // historically represented differently. Keep it in instrumentation so CI
        // validates the exact framework semantics used by production.
        assertTrue(state.isNull("activeProfile"))
        if (Build.VERSION.SDK_INT >= 37) {
            assertEquals("", state.optString("activeProfile"))
        } else {
            assertEquals("null", state.optString("activeProfile"))
        }

        val inactive = LegacyIdentityMarkers.desiredState(state)
        assertFalse(inactive.engine)
        assertFalse(inactive.build)
        assertFalse(inactive.region)

        state.put("activeProfile", "null")
        val active = LegacyIdentityMarkers.desiredState(state)
        assertTrue(active.engine)
        assertTrue(active.build)
        assertTrue(active.region)
    }

    @Test
    fun `sentinel-like profile names remain ordinary strings on Android JSON`() {
        listOf("null", "true", "false", "0", "undefined").forEach { name ->
            val profile =
                JSONObject()
                    .put("name", name)
                    .put("features", JSONObject().put("buildIdentity", true))
            val state = policyState(profile, JSONObject.NULL)

            assertFalse("JSON null must not activate profile $name", LegacyIdentityMarkers.desiredState(state).build)

            state.put("activeProfile", name)
            val active = LegacyIdentityMarkers.desiredState(state)
            assertTrue("literal profile name $name must remain selectable", active.engine)
            assertTrue("literal profile name $name must apply its override", active.build)
        }
    }

    @Test
    fun `marker transaction round trips on Android filesystem`() {
        val root = newTempRoot("identity-marker-roundtrip")
        val originalSecureFileImpl = SecureFile.impl
        SecureFile.impl = simpleSecureFileOperations()
        try {
            val features =
                JSONObject()
                    .put("buildIdentity", true)
                    .put("attestationIdentity", false)
                    .put("telephonyIdentity", true)
                    .put("regionIdentity", false)
                    .put("identityRefresh", false)
                    .put("securityPatch", false)
            val state = policyState(JSONObject().put("name", "unused"), JSONObject.NULL, features)
            BootPolicyProjection.write(root, state)

            LegacyIdentityMarkers.apply(
                LegacyIdentityMarkers.plan(root, LegacyIdentityMarkers.desiredState(state)),
                refreshRuntime = {},
            )

            assertTrue(File(root, LegacyIdentityMarkers.ENGINE).isFile)
            assertTrue(File(root, LegacyIdentityMarkers.BUILD).isFile)
            assertTrue(File(root, LegacyIdentityMarkers.TELEPHONY).isFile)
            assertFalse(File(root, LegacyIdentityMarkers.REGION).exists())
            assertTrue(LegacyIdentityMarkers.isSynchronized(root, state).getOrThrow())

            features.put("buildIdentity", false)
            features.put("telephonyIdentity", false)
            BootPolicyProjection.write(root, state)
            LegacyIdentityMarkers.apply(
                LegacyIdentityMarkers.plan(root, LegacyIdentityMarkers.desiredState(state)),
                refreshRuntime = {},
            )

            assertFalse(File(root, LegacyIdentityMarkers.ENGINE).exists())
            assertFalse(File(root, LegacyIdentityMarkers.BUILD).exists())
            assertFalse(File(root, LegacyIdentityMarkers.TELEPHONY).exists())
            assertTrue(LegacyIdentityMarkers.isSynchronized(root, state).getOrThrow())
        } finally {
            SecureFile.impl = originalSecureFileImpl
            root.deleteRecursively()
        }
    }

    @Test
    fun `partial secure touch is rolled back on Android filesystem`() {
        val root = newTempRoot("identity-marker-rollback")
        val originalSecureFileImpl = SecureFile.impl
        SecureFile.impl = simpleSecureFileOperations(failAfterCreateName = LegacyIdentityMarkers.BUILD)
        try {
            val desired =
                LegacyIdentityMarkers.DesiredState(
                    engine = true,
                    build = true,
                    telephony = false,
                    region = false,
                    refresh = false,
                )

            val result =
                runCatching {
                    LegacyIdentityMarkers.apply(
                        LegacyIdentityMarkers.plan(root, desired),
                        refreshRuntime = {},
                    )
                }

            assertTrue(result.isFailure)
            assertFalse("earlier marker must roll back", File(root, LegacyIdentityMarkers.ENGINE).exists())
            assertFalse("partially-created current marker must roll back", File(root, LegacyIdentityMarkers.BUILD).exists())
        } finally {
            SecureFile.impl = originalSecureFileImpl
            root.deleteRecursively()
        }
    }

    private fun policyState(
        profile: JSONObject,
        activeProfile: Any,
        features: JSONObject =
            JSONObject()
                .put("buildIdentity", false)
                .put("attestationIdentity", false)
                .put("telephonyIdentity", false)
                .put("regionIdentity", false)
                .put("identityRefresh", false)
                .put("securityPatch", false),
    ): JSONObject =
        JSONObject()
            .put("source", "v2")
            .put("features", features)
            .put("profiles", JSONArray().put(profile))
            .put("activeProfile", activeProfile)

    private fun newTempRoot(prefix: String): File {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return Files.createTempDirectory(cache.toPath(), prefix).toFile()
    }

    private fun simpleSecureFileOperations(failAfterCreateName: String? = null): SecureFileOperations =
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
                if (!file.exists() && !file.mkdirs()) {
                    throw IOException("Could not create ${file.absolutePath}")
                }
            }

            override fun touch(
                file: File,
                mode: Int,
            ) {
                file.parentFile?.mkdirs()
                if (!file.exists() && !file.createNewFile()) {
                    throw IOException("Could not create ${file.absolutePath}")
                }
                if (file.name == failAfterCreateName) {
                    throw IOException("Injected failure after creating ${file.name}")
                }
            }
        }
}
