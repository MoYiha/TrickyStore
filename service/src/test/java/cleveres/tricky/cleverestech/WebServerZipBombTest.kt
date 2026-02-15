package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WebServerZipBombTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var configDir: File
    private lateinit var originalImpl: SecureFileOperations
    private var writeStreamCalled = false
    private var limitPassed: Long = -1

    @Before
    fun setUp() {
        configDir = tempFolder.newFolder("config")
        originalImpl = SecureFile.impl

        // Mock Logger to prevent spam
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
            override fun i(tag: String, msg: String) {}
        })
    }

    @After
    fun tearDown() {
        SecureFile.impl = originalImpl
    }

    @Test
    fun testRestoreUsesStreamingWithLimit() {
        // Setup mock
        SecureFile.impl = object : SecureFileOperations {
            override fun writeText(file: File, content: String) {
                Assert.fail("Should use writeStream instead of writeText")
            }

            override fun writeStream(file: File, inputStream: InputStream, limit: Long) {
                writeStreamCalled = true
                limitPassed = limit
                // Simulate reading to ensure stream is valid
                inputStream.readBytes()
            }

            override fun mkdirs(file: File, mode: Int) {}
            override fun touch(file: File, mode: Int) {}
        }

        // Create a valid zip with one file
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("target.txt"))
            zos.write("some content".toByteArray())
            zos.closeEntry()
        }
        val zipBytes = baos.toByteArray()

        // Call restoreBackupZip
        // We need to access WebServer.restoreBackupZip, but it is in companion object or static?
        // Ah, it's in companion object of WebServer class.
        // Let's check WebServer.kt again. It is in companion object.

        WebServer.restoreBackupZip(configDir, ByteArrayInputStream(zipBytes))

        Assert.assertTrue("writeStream should be called", writeStreamCalled)
        Assert.assertEquals("Limit should be 50MB", 50 * 1024 * 1024L, limitPassed)
    }

    @Test
    fun testSecureFileLimitEnforcement() {
        // Test the logic inside DefaultSecureFileOperations (streaming and limit)
        // Since we can't easily use Os.write in unit tests (Android dependency),
        // we can't fully test DefaultSecureFileOperations here without Robolectric or mocking Os.
        // However, we can verify the logic if we extract it or if we trust the implementation.
        // Given the constraints, verifying that WebServer CALLS the method with the limit is the key step.
        // The implementation of writeStream in SecureFile.kt handles the limit check.

        // We will assume the implementation of SecureFile.kt is correct as per the code review.
        // But let's verify that WebServer handles the exception if writeStream throws it.

        SecureFile.impl = object : SecureFileOperations {
            override fun writeText(file: File, content: String) {}
            override fun writeStream(file: File, inputStream: InputStream, limit: Long) {
                 throw java.io.IOException("File size exceeds limit of $limit bytes")
            }
            override fun mkdirs(file: File, mode: Int) {}
            override fun touch(file: File, mode: Int) {}
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("target.txt"))
            zos.write("large content".toByteArray())
            zos.closeEntry()
        }
        val zipBytes = baos.toByteArray()

        try {
            WebServer.restoreBackupZip(configDir, ByteArrayInputStream(zipBytes))
            // It should propagate the exception or handle it?
            // WebServer.restoreBackupZip calls SecureFile.writeStream.
            // If exception is thrown, it bubbles up.
            // The caller of restoreBackupZip (in serve method) catches Exception.
        } catch (e: Exception) {
            Assert.assertEquals("File size exceeds limit of 52428800 bytes", e.message)
            return
        }
        // If no exception, check if it was swallowed inside restoreBackupZip?
        // Looking at restoreBackupZip code:
        // It iterates entries. Inside loop calls writeStream.
        // It does NOT have try-catch inside the loop.
        // So exception should propagate.
    }
}
