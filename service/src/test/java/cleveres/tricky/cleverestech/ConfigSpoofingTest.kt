package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class ConfigSpoofingTest {

    private lateinit var tempDir: File
    private lateinit var originalImpl: SecureFileOperations
    private lateinit var originalRoot: File

    class MockSecureFileOperations : SecureFileOperations {
        override fun writeText(file: File, content: String) {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }

        override fun writeStream(file: File, inputStream: InputStream, limit: Long) {
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
        }

        override fun mkdirs(file: File, mode: Int) {
            file.mkdirs()
        }

        override fun touch(file: File, mode: Int) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("test_config_spoof").toFile()
        tempDir.deleteOnExit()

        // Save original state
        originalImpl = SecureFile.impl

        // Replace SecureFile implementation with mock
        SecureFile.impl = MockSecureFileOperations()

        // Initialize Config with temp dir by using reflection or just ensuring paths are relative if possible.
        // But Config.root is private and initialized to "/data/adb/cleverestricky".
        // We need to use reflection to set 'root' in Config to our temp dir.
        val rootField = Config::class.java.getDeclaredField("root")
        rootField.isAccessible = true
        originalRoot = rootField.get(Config) as File
        rootField.set(Config, tempDir)

        // Reset Config state
        val buildVarsField = Config::class.java.getDeclaredField("buildVars")
        buildVarsField.isAccessible = true
        buildVarsField.set(Config, emptyMap<String, String>())

        // Clear templateKeyCache to avoid stale cache from other tests
        val cacheField = Config::class.java.getDeclaredField("templateKeyCache")
        cacheField.isAccessible = true
        (cacheField.get(Config) as MutableMap<*, *>).clear()
    }

    @org.junit.After
    fun tearDown() {
        // Restore SecureFile implementation
        SecureFile.impl = originalImpl

        // Restore Config root
        val rootField = Config::class.java.getDeclaredField("root")
        rootField.isAccessible = true
        rootField.set(Config, originalRoot)
    }

    @Test
    fun testExtendedSpoofingSupport() {
        val spoofVars = File(tempDir, "spoof_build_vars")
        spoofVars.writeText("""
            BOARD=test_board
            HARDWARE=test_hardware
            DISPLAY=test_display
            HOST=test_host
            USER=test_user
            SDK_INT=34
            PREVIEW_SDK=1
            CODENAME=UpsideDownCake
            BOOTLOADER=test_bootloader
            TIMESTAMP=1234567890
            ID=test_id
        """.trimIndent())

        // Trigger update
        Config.updateBuildVars(spoofVars)

        // verify mappings
        assertEquals("test_board", Config.getBuildVar("ro.product.board"))
        assertEquals("test_board", Config.getBuildVar("ro.board.platform"))
        assertEquals("test_hardware", Config.getBuildVar("ro.hardware"))
        assertEquals("test_display", Config.getBuildVar("ro.build.display.id"))
        assertEquals("test_host", Config.getBuildVar("ro.build.host"))
        assertEquals("test_user", Config.getBuildVar("ro.build.user"))
        assertEquals("34", Config.getBuildVar("ro.build.version.sdk"))
        assertEquals("1", Config.getBuildVar("ro.build.version.preview_sdk"))
        assertEquals("UpsideDownCake", Config.getBuildVar("ro.build.version.codename"))
        assertEquals("test_bootloader", Config.getBuildVar("ro.bootloader"))
        assertEquals("1234567890", Config.getBuildVar("ro.build.date.utc"))

        // Verify ID is separate if display is set
        assertEquals("test_id", Config.getBuildVar("ro.build.id"))
    }

    @Test
    fun testDisplayFallbackToId() {
        val spoofVars = File(tempDir, "spoof_build_vars")
        spoofVars.writeText("""
            ID=fallback_id
        """.trimIndent())

        // Trigger update
        Config.updateBuildVars(spoofVars)

        // Verify fallback
        // ro.build.display.id should return "fallback_id" because DISPLAY is missing
        assertEquals("fallback_id", Config.getBuildVar("ro.build.display.id"))
        assertEquals("fallback_id", Config.getBuildVar("ro.build.id"))
    }
}
