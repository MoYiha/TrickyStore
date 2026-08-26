package cleveres.tricky.cleverestech

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DeepBugSweepRegressionTest {
    @Test
    fun `bounded backup copy rejects source growth past entry limit`() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
        val output = ByteArrayOutputStream()

        assertThrows(IOException::class.java) {
            BackupIo.copyBounded(input, output, entryLimit = 4, remainingTotal = 100)
        }
        assertTrue(output.size() <= 4)
    }

    @Test
    fun `bounded backup copy accounts for actual streamed bytes`() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        val copied =
            BackupIo.copyBounded(
                ByteArrayInputStream(expected),
                output,
                entryLimit = 16,
                remainingTotal = 16,
            )

        assertEquals(expected.size.toLong(), copied)
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun `restore transaction rolls back every earlier mutation after failure`() {
        val root = Files.createTempDirectory("cleveres-restore-rollback").toFile()
        try {
            val first = root.resolve("first.txt").apply { writeText("old-first") }
            val second = root.resolve("second.txt").apply { writeText("old-second") }
            val created = root.resolve("created.txt")

            val mutations =
                listOf(
                    BackupRestoreTransaction.Mutation(first, "new-first".toByteArray()),
                    BackupRestoreTransaction.Mutation(second, null),
                    BackupRestoreTransaction.Mutation(created, "new-created".toByteArray()),
                )

            assertThrows(IOException::class.java) {
                BackupRestoreTransaction.apply(root, mutations) { index, _ ->
                    if (index == 2) throw IOException("injected restore failure")
                }
            }

            assertEquals("old-first", first.readText())
            assertEquals("old-second", second.readText())
            assertFalse(created.exists())
            assertFalse(root.listFiles().orEmpty().any { it.name.startsWith(".restore-txn-") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `restore transaction rolls back disk before invoking runtime rollback`() {
        val root = Files.createTempDirectory("cleveres-restore-runtime-rollback").toFile()
        try {
            val target = root.resolve("target.txt").apply { writeText("old-target") }
            var rollbackCalled = false
            val zip = ByteArrayOutputStream()
            ZipOutputStream(zip).use { zos ->
                zos.putNextEntry(ZipEntry("target.txt"))
                zos.write("new_target".toByteArray())
                zos.closeEntry()
            }

            assertThrows(IOException::class.java) {
                WebServer.restoreBackupZip(
                    root,
                    ByteArrayInputStream(zip.toByteArray()),
                    afterMutation = {
                        assertEquals("new_target", target.readText())
                        throw IOException("injected runtime refresh failure")
                    },
                    onRollback = {
                        rollbackCalled = true
                        assertEquals("old-target", target.readText())
                    },
                )
            }

            assertTrue(rollbackCalled)
            assertEquals("old-target", target.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `invalid backup entry is zeroized when validation fails`() {
        val root = Files.createTempDirectory("cleveres-backup-zeroize").toFile()
        var wiped: ByteArray? = null
        try {
            val zip = ByteArrayOutputStream()
            ZipOutputStream(zip).use { zos ->
                zos.putNextEntry(ZipEntry("keyboxes/invalid.cbox"))
                zos.write(byteArrayOf(1, 2, 3, 4, 5))
                zos.closeEntry()
            }
            WebServer.backupEntryWipeObserver = { wiped = it.copyOf() }

            assertThrows(IOException::class.java) {
                WebServer.restoreBackupZip(root, ByteArrayInputStream(zip.toByteArray()))
            }

            assertNotNull(wiped)
            assertTrue(requireNotNull(wiped).all { it == 0.toByte() })
        } finally {
            WebServer.backupEntryWipeObserver = null
            root.deleteRecursively()
        }
    }

    @Test
    fun `published policy validation rejects unavailable references without changing active state`() {
        val root = Files.createTempDirectory("cleveres-policy-validation").toFile()
        try {
            PolicyState.setRootForTesting(root)
            val validState =
                """
                {"version":2,"features":{"buildIdentity":false,"attestationIdentity":false,"telephonyIdentity":false,"regionIdentity":false,"identityRefresh":false,"securityPatch":false},"securityPatch":{"automaticThresholdMonths":6,"system":{"mode":"device_default"},"vendor":{"mode":"device_default"},"boot":{"mode":"device_default"}},"profiles":[],"activeProfile":null}
                """.trimIndent()
            PolicyState.installStateForTesting(validState)
            File(root, PolicyState.STATE_FILE).writeText(
                JSONObject(validState).put(
                    "profiles",
                    org.json.JSONArray().put(
                        org.json.JSONObject()
                            .put("name", "missing-keybox")
                            .put("enabled", true)
                            .put("applications", org.json.JSONArray().put("com.example.app"))
                            .put("template", org.json.JSONObject.NULL)
                            .put("keybox", "missing.xml")
                            .put("privacy", "inherit")
                            .put("features", org.json.JSONObject())
                            .put("securityPatch", org.json.JSONObject())
                            .put("rkpPassthrough", org.json.JSONObject.NULL)
                            .put("drmPassthrough", org.json.JSONObject.NULL),
                    ),
                ).toString(),
            )

            assertTrue(PolicyState.validatePublishedState().isFailure)
            assertFalse(PolicyState.stateJson().getJSONObject("features").getBoolean("buildIdentity"))
        } finally {
            PolicyState.resetForTesting()
            root.deleteRecursively()
        }
    }

    @Test
    fun `restore transaction commits replacements creations and deletions together`() {
        val root = Files.createTempDirectory("cleveres-restore-commit").toFile()
        try {
            val first = root.resolve("first.txt").apply { writeText("old-first") }
            val second = root.resolve("second.txt").apply { writeText("old-second") }
            val created = root.resolve("created.txt")

            BackupRestoreTransaction.apply(
                root,
                listOf(
                    BackupRestoreTransaction.Mutation(first, "new-first".toByteArray()),
                    BackupRestoreTransaction.Mutation(second, null),
                    BackupRestoreTransaction.Mutation(created, "new-created".toByteArray()),
                ),
            )

            assertEquals("new-first", first.readText())
            assertFalse(second.exists())
            assertEquals("new-created", created.readText())
            assertFalse(root.listFiles().orEmpty().any { it.name.startsWith(".restore-txn-") })
        } finally {
            root.deleteRecursively()
        }
    }
}
