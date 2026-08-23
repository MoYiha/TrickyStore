package cleveres.tricky.cleverestech

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PolicyMigrationNullSemanticsTest {
    @Test
    fun `migration preserves json null when a literal null profile exists`() {
        withPolicyRoot(activeProfile = JSONObject.NULL) { root, stateFile ->
            assertFalse(PolicyMigration.sanitize(root))

            val migrated = JSONObject(stateFile.readText())
            assertTrue(migrated.isNull("activeProfile"))
            val profile = migrated.getJSONArray("profiles").getJSONObject(0)
            assertEquals("null", profile.getString("name"))
            assertTrue(profile.isNull("keybox"))
        }
    }

    @Test
    fun `migration preserves explicitly selected literal null profile`() {
        withPolicyRoot(activeProfile = "null") { root, stateFile ->
            assertFalse(PolicyMigration.sanitize(root))

            val migrated = JSONObject(stateFile.readText())
            assertEquals("null", migrated.getString("activeProfile"))
        }
    }

    private fun withPolicyRoot(
        activeProfile: Any,
        block: (File, File) -> Unit,
    ) {
        val root = Files.createTempDirectory("policy-migration-null-test").toFile()
        try {
            val text = policy(activeProfile).toString()
            val stateFile = File(root, PolicyState.STATE_FILE).apply { writeText(text) }
            File(root, PolicyState.LAST_GOOD_FILE).writeText(text)
            block(root, stateFile)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun policy(activeProfile: Any): JSONObject {
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
                .put("template", JSONObject.NULL)
                .put("keybox", JSONObject.NULL)
                .put("privacy", "inherit")
                .put("features", JSONObject().put("buildIdentity", true))
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
            .put("activeProfile", activeProfile)
    }
}
