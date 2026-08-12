package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PolicyMigrationTest {
    private lateinit var tempDir: File
    private lateinit var originalSecureFileImpl: SecureFileOperations

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("cleverestricky-policy-migration").toFile()
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
        tempDir.deleteRecursively()
    }

    @Test
    fun staleKeyboxAndRetiredRkpFieldsAreRepairedWithoutResettingProfiles() {
        val state =
            JSONObject(
                """
                {
                  "version": 2,
                  "profiles": [
                    {
                      "name": "Daily",
                      "keybox": "missing.xml",
                      "rkpPassthrough": true
                    }
                  ],
                  "activeProfile": "Daily"
                }
                """.trimIndent(),
            )
        val stateFile = File(tempDir, "policy_state_v2.json")
        stateFile.writeText(state.toString())

        assertTrue(PolicyMigration.sanitize(tempDir))

        val repaired = JSONObject(stateFile.readText())
        val profile = repaired.getJSONArray("profiles").getJSONObject(0)
        assertTrue(profile.isNull("keybox"))
        assertFalse(profile.has("rkpPassthrough"))
        assertTrue(repaired.optString("activeProfile") == "Daily")
    }

    @Test
    fun validManagedKeyboxReferenceIsPreserved() {
        val keyboxDir = File(tempDir, "keyboxes")
        keyboxDir.mkdirs()
        File(keyboxDir, "valid.xml").writeText("placeholder")
        val stateFile = File(tempDir, "policy_state_v2.json")
        stateFile.writeText(
            """
            {
              "version": 2,
              "profiles": [
                {
                  "name": "Daily",
                  "keybox": "valid.xml"
                }
              ],
              "activeProfile": "Daily"
            }
            """.trimIndent(),
        )

        assertFalse(PolicyMigration.sanitize(tempDir))
        val preserved = JSONObject(stateFile.readText())
        assertTrue(preserved.getJSONArray("profiles").getJSONObject(0).getString("keybox") == "valid.xml")
    }
}
