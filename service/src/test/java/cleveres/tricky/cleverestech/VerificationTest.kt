package cleveres.tricky.cleverestech

import java.io.File
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import java.security.MessageDigest

class VerificationTest {

    private val tempDir = File("temp_verification_test")

    @Before
    fun setup() {
        tempDir.mkdir()
        // Mock logger
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) { println("DEBUG: $tag: $msg") }
            override fun e(tag: String, msg: String) { println("ERROR: $tag: $msg") }
            override fun e(tag: String, msg: String, t: Throwable?) { println("ERROR: $tag: $msg $t") }
            override fun i(tag: String, msg: String) { println("INFO: $tag: $msg") }
        })

        // Create a dummy file
        val file = File(tempDir, "test.sh")
        file.writeText("original content")

        // Create checksum
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = "original content".toByteArray()
        md.update(bytes)
        val checksum = md.digest().joinToString("") { "%02x".format(it) }
        File(tempDir, "test.sh.sha256").writeText(checksum)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testVerificationPasses() {
        var exitCode = -1
        Verification.exitProcessImpl = { exitCode = it }

        Verification.check(tempDir)

        assertEquals(-1, exitCode)
    }

    @Test
    fun testVerificationFailsOnModifiedFile() {
        var exitCode = -1
        Verification.exitProcessImpl = { exitCode = it }

        // Modify file
        File(tempDir, "test.sh").writeText("modified content")

        Verification.check(tempDir)

        // Now it should NOT exit
        assertEquals(-1, exitCode)
        // And NOT create disable file
        assertFalse(File(tempDir, "disable").exists())
    }

    @Test
    fun testVerificationFailsOnMissingChecksum() {
        var exitCode = -1
        Verification.exitProcessImpl = { exitCode = it }

        // Remove checksum
        File(tempDir, "test.sh.sha256").delete()

        Verification.check(tempDir)

        // Now it should NOT exit
        assertEquals(-1, exitCode)
    }
}
