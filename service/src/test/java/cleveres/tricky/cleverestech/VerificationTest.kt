package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class VerificationTest {
    private val tempDir = File("temp_verification_test")

    @Before
    fun setup() {
        tempDir.mkdir()
        Logger.setImpl(
            object : Logger.LogImpl {
                override fun d(
                    tag: String,
                    msg: String,
                ) {
                    // no-op
                }

                override fun e(
                    tag: String,
                    msg: String,
                ) {
                    // no-op
                }

                override fun e(
                    tag: String,
                    msg: String,
                    t: Throwable?,
                ) {
                    // no-op
                }

                override fun i(
                    tag: String,
                    msg: String,
                ) {
                    // no-op
                }
            },
        )

        val file = File(tempDir, "test.sh")
        file.writeText("original content")
        writeChecksum(file)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testVerificationPasses() {
        assertTrue(Verification.check(tempDir))
    }

    @Test
    fun testVerificationFailsOnModifiedFile() {
        File(tempDir, "test.sh").writeText("modified content")

        assertFalse(Verification.check(tempDir))
        assertFalse(File(tempDir, "disable").exists())
    }

    @Test
    fun testVerificationFailsOnMissingChecksum() {
        File(tempDir, "test.sh.sha256").delete()

        assertFalse(Verification.check(tempDir))
    }

    @Test
    fun testVerificationFailsOnMissingTarget() {
        File(tempDir, "test.sh").delete()

        assertFalse(Verification.check(tempDir))
    }

    @Test
    fun testVerificationFailsOnMalformedChecksum() {
        File(tempDir, "test.sh.sha256").writeText("not-a-sha256")

        assertFalse(Verification.check(tempDir))
    }

    @Test
    fun testVerificationFailsOnOversizedChecksum() {
        File(tempDir, "test.sh.sha256").writeText("a".repeat(1025))

        assertFalse(Verification.check(tempDir))
    }

    @Test
    fun testVerificationFailsOnSymbolicLink() {
        val target =
            File.createTempFile("verification-link-target", ".tmp").apply {
                writeText("data")
                deleteOnExit()
            }
        val digest = MessageDigest.getInstance("SHA-256").digest("data".toByteArray())
        File(tempDir, "linked.sha256").writeText(digest.joinToString("") { "%02x".format(it) })
        java.nio.file.Files.createSymbolicLink(File(tempDir, "linked").toPath(), target.toPath().toAbsolutePath())

        assertFalse(Verification.check(tempDir))
    }

    @Test
    fun testVerificationFailsOnUncheckedInjectedPayload() {
        File(tempDir, "unexpected.sh").writeText("malicious payload")

        assertFalse(Verification.check(tempDir))
    }

    @Test
    fun testVerificationFailsOnUncheckedSystemProp() {
        File(tempDir, "system.prop").writeText("ro.example.injected=1")

        assertFalse(Verification.check(tempDir))
    }

    @Test
    fun managerStateFilesMayRemainUnchecked() {
        for (name in listOf("disable", "remove", "update", "tampered", "supervisor.pid", "daemon.pid", "adapter.pid", "backend.pid")) {
            File(tempDir, name).writeText("")
        }

        assertTrue(Verification.check(tempDir))
    }

    private fun writeChecksum(file: File) {
        val md = MessageDigest.getInstance("SHA-256")
        file.forEachBlock { buffer, bytesRead -> md.update(buffer, 0, bytesRead) }
        File(file.path + ".sha256").writeText(md.digest().joinToString("") { "%02x".format(it) })
    }
}
