package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class IntegrityViolationHandlerTest {

    private val disableCalls = AtomicInteger(0)
    private val rebootCalls = AtomicInteger(0)
    private val terminateCalls = AtomicInteger(0)
    private val terminateExitCodes = CopyOnWriteArrayList<Int>()
    private val disabledPaths = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        IntegrityViolationHandler.resetForTesting()
        disableCalls.set(0)
        rebootCalls.set(0)
        terminateCalls.set(0)
        terminateExitCodes.clear()
        disabledPaths.clear()
        IntegrityViolationHandler.disableModule = { path ->
            disabledPaths.add(path)
            disableCalls.incrementAndGet()
            true
        }
        IntegrityViolationHandler.rebootSystem = {
            rebootCalls.incrementAndGet()
        }
        IntegrityViolationHandler.terminateProcess = { code ->
            terminateCalls.incrementAndGet()
            terminateExitCodes.add(code)
        }
    }

    @After
    fun tearDown() {
        IntegrityViolationHandler.resetForTesting()
    }

    @Test
    fun singleViolationDisablesAndRebootsWithoutDeletingModule() {
        IntegrityViolationHandler.handleViolation(listOf("test violation"))
        assertTrue(IntegrityViolationHandler.isViolated)
        assertEquals(1, disableCalls.get())
        assertEquals(1, rebootCalls.get())
        assertEquals(0, terminateCalls.get())
    }

    @Test
    fun multipleViolationsAreIdempotent() {
        IntegrityViolationHandler.handleViolation(listOf("first"))
        IntegrityViolationHandler.handleViolation(listOf("second"))
        IntegrityViolationHandler.handleViolation(listOf("third"))
        assertEquals(1, disableCalls.get())
        assertEquals(1, rebootCalls.get())
        assertEquals(0, terminateCalls.get())
    }

    @Test
    fun concurrentViolationsAreIdempotent() {
        val latch = CountDownLatch(1)
        val threads =
            (1..10).map { i ->
                Thread {
                    latch.await()
                    IntegrityViolationHandler.handleViolation(listOf("concurrent $i"))
                }
            }
        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }
        assertEquals(1, disableCalls.get())
        assertEquals(1, rebootCalls.get())
        assertEquals(0, terminateCalls.get())
        assertTrue(IntegrityViolationHandler.isViolated)
    }

    @Test
    fun disableFailureStillSetsViolatedFlagAndAbortsReboot() {
        IntegrityViolationHandler.disableModule = { false }
        IntegrityViolationHandler.handleViolation(listOf("disable will fail"))
        assertTrue(IntegrityViolationHandler.isViolated)
        assertEquals(0, rebootCalls.get())
        assertEquals(1, terminateCalls.get())
        assertEquals(1, terminateExitCodes[0])
    }

    @Test
    fun disableExceptionStillSetsViolatedFlagAndAbortsReboot() {
        IntegrityViolationHandler.disableModule = { throw RuntimeException("I/O error") }
        IntegrityViolationHandler.handleViolation(listOf("disable throws"))
        assertTrue(IntegrityViolationHandler.isViolated)
        assertEquals(0, rebootCalls.get())
        assertEquals(1, terminateCalls.get())
        assertEquals(1, terminateExitCodes[0])
    }

    @Test
    fun rebootFailureStillKeepsViolatedFlag() {
        IntegrityViolationHandler.rebootSystem = { throw RuntimeException("reboot failed") }
        IntegrityViolationHandler.handleViolation(listOf("reboot fails"))
        assertTrue(IntegrityViolationHandler.isViolated)
        assertEquals(1, disableCalls.get())
        assertEquals(1, terminateCalls.get())
        assertEquals(1, terminateExitCodes[0])
    }

    @Test
    fun notViolatedBeforeHandleViolation() {
        assertFalse(IntegrityViolationHandler.isViolated)
    }

    @Test
    fun resetForTestingClearsState() {
        IntegrityViolationHandler.handleViolation(listOf("test"))
        assertTrue(IntegrityViolationHandler.isViolated)
        IntegrityViolationHandler.resetForTesting()
        assertFalse(IntegrityViolationHandler.isViolated)
    }

    @Test
    fun disabledPathMatchesModuleDir() {
        IntegrityViolationHandler.handleViolation(listOf("check path"))
        assertEquals(1, disabledPaths.size)
        assertTrue(disabledPaths[0].contains("cleverestricky"))
    }

    @Test
    fun disableMarkerCreationPreservesInstalledFiles() {
        val root = java.nio.file.Files.createTempDirectory("test_module_root")
        try {
            val payload = root.resolve("payload.bin")
            java.nio.file.Files.write(payload, byteArrayOf(1, 2, 3, 4))

            assertTrue(createDisableMarker(root))

            assertTrue(java.nio.file.Files.exists(root))
            assertTrue(java.nio.file.Files.exists(payload))
            assertEquals(listOf<Byte>(1, 2, 3, 4), java.nio.file.Files.readAllBytes(payload).toList())
            assertTrue(
                java.nio.file.Files.isRegularFile(
                    root.resolve("disable"),
                    java.nio.file.LinkOption.NOFOLLOW_LINKS,
                ),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun disableMarkerCreationIsIdempotent() {
        val root = java.nio.file.Files.createTempDirectory("test_module_idempotent")
        try {
            assertTrue(createDisableMarker(root))
            assertTrue(createDisableMarker(root))
            assertEquals(0L, java.nio.file.Files.size(root.resolve("disable")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun disableMarkerRejectsExistingDirectory() {
        val root = java.nio.file.Files.createTempDirectory("test_module_bad_marker")
        try {
            java.nio.file.Files.createDirectory(root.resolve("disable"))
            assertFalse(createDisableMarker(root))
            assertTrue(java.nio.file.Files.isDirectory(root.resolve("disable")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun disableMarkerRejectsSymlinkAndPreservesExternalTarget() {
        val root = java.nio.file.Files.createTempDirectory("test_module_symlink")
        val external = java.nio.file.Files.createTempFile("test_disable_external", ".txt")
        java.nio.file.Files.writeString(external, "preserve me")
        try {
            try {
                java.nio.file.Files.createSymbolicLink(root.resolve("disable"), external)
            } catch (_: Exception) {
                return
            }

            assertFalse(createDisableMarker(root))
            assertTrue(java.nio.file.Files.exists(external))
            assertEquals("preserve me", java.nio.file.Files.readString(external))
        } finally {
            root.toFile().deleteRecursively()
            java.nio.file.Files.deleteIfExists(external)
        }
    }
}
