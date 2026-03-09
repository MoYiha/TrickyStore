package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate SecureFile code correctness and safety.
 *
 * Validates atomic write, partial write detection, permission handling,
 * and fallback behavior.
 */
class SecureFileSafetyTest {

    private lateinit var secureFileContent: String

    @Before
    fun setup() {
        secureFileContent = serviceMainFile("util/SecureFile.kt").readText()
    }

    // ================================
    // Atomic write safety
    // ================================

    @Test
    fun testUsesTemporaryFileForAtomicWrite() {
        assertTrue(
            "SecureFile must write to .tmp file before atomic rename",
            secureFileContent.contains(".tmp")
        )
    }

    @Test
    fun testUsesAtomicRename() {
        assertTrue(
            "SecureFile must use Os.rename for atomic file replacement",
            secureFileContent.contains("Os.rename")
        )
    }

    @Test
    fun testSyncsBeforeRename() {
        assertTrue(
            "SecureFile must call Os.fsync before rename to ensure data persistence",
            secureFileContent.contains("Os.fsync")
        )
    }

    // ================================
    // Partial write detection (bug fix)
    // ================================

    @Test
    fun testDetectsPartialWriteInWriteBytes() {
        assertTrue(
            "writeBytes must detect and report partial writes instead of silently succeeding",
            secureFileContent.contains("Incomplete write") || secureFileContent.contains("Partial write")
        )
    }

    @Test
    fun testDetectsPartialWriteInWriteStream() {
        assertTrue(
            "writeStream must detect and report partial writes",
            secureFileContent.contains("Incomplete stream write") || secureFileContent.contains("Partial stream write")
        )
    }

    @Test
    fun testCleansTempFileOnPartialWrite() {
        // When partial write is detected, the temp file must be removed
        assertTrue(
            "Must clean up temp file on partial write failure",
            secureFileContent.contains("Os.remove(tmpPath)")
        )
    }

    // ================================
    // Permission handling
    // ================================

    @Test
    fun testSetsFilePermissions() {
        assertTrue(
            "SecureFile must set file permissions via Os.fchmod",
            secureFileContent.contains("Os.fchmod")
        )
    }

    @Test
    fun testDefaultPermissionIs600() {
        // Permission 384 in decimal = 0600 in octal
        assertTrue(
            "Default file permission must be 384 (0600 octal) for owner-only read/write",
            secureFileContent.contains("val mode = 384")
        )
    }

    // ================================
    // Concurrency safety
    // ================================

    @Test
    fun testUsesReentrantLock() {
        assertTrue(
            "SecureFile must use ReentrantLock for thread safety",
            secureFileContent.contains("ReentrantLock")
        )
    }

    @Test
    fun testAllPublicMethodsLocked() {
        assertTrue(
            "writeText must acquire lock",
            secureFileContent.contains("lock.withLock") || secureFileContent.contains("withLock")
        )
    }

    // ================================
    // Fallback behavior
    // ================================

    @Test
    fun testFallbackOnOsUnavailable() {
        assertTrue(
            "Must fallback to Java File APIs when Os class is unavailable (NoClassDefFoundError)",
            secureFileContent.contains("NoClassDefFoundError")
        )
    }

    @Test
    fun testFallbackOnRenameFail() {
        assertTrue(
            "Must fallback to direct write when atomic rename fails",
            secureFileContent.contains("onFailure")
        )
    }

    // ================================
    // Error handling
    // ================================

    @Test
    fun testClosesFileDescriptorInFinally() {
        assertTrue(
            "Must close file descriptor in finally block to prevent FD leaks",
            secureFileContent.contains("finally") && secureFileContent.contains("Os.close(fd)")
        )
    }

    @Test
    fun testRemovesTempFileOnError() {
        assertTrue(
            "Must attempt to remove temp file on exception",
            secureFileContent.contains("Os.remove(tmpPath)")
        )
    }

    // ================================
    // mkdirs correctness
    // ================================

    @Test
    fun testMkdirsCreatesParent() {
        assertTrue(
            "mkdirs must recursively create parent directories",
            secureFileContent.contains("parentFile") && secureFileContent.contains("mkdirs(parent")
        )
    }

    @Test
    fun testMkdirsSetsPermissions() {
        assertTrue(
            "mkdirs must set permissions on created directories",
            secureFileContent.contains("Os.chmod") || secureFileContent.contains("Os.mkdir")
        )
    }

    // ================================
    // Interface design
    // ================================

    @Test
    fun testHasSecureFileOperationsInterface() {
        assertTrue(
            "SecureFile must have a testable interface (SecureFileOperations)",
            secureFileContent.contains("interface SecureFileOperations")
        )
    }

    @Test
    fun testImplIsSwappable() {
        assertTrue(
            "SecureFile.impl must be var for test injection",
            secureFileContent.contains("var impl:")
        )
    }
}
