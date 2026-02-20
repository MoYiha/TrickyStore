package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations

class ConfigTemplateTest {

    private lateinit var originalImpl: SecureFileOperations

    @Before
    fun setUp() {
        // Mock SecureFile
        originalImpl = SecureFile.impl
        SecureFile.impl = MockSecureFileOperations()

        val tempDir = java.nio.file.Files.createTempDirectory("test_config_template").toFile()
        tempDir.deleteOnExit()

        // Initialize DeviceTemplateManager with built-ins
        DeviceTemplateManager.initialize(tempDir)

        // Force Config to reload templates from Manager
        Config.updateCustomTemplates(null)
    }

    @After
    fun tearDown() {
        SecureFile.impl = originalImpl
    }

    @Test
    fun testUpdateBuildVars_withTemplate() {
        // Create a temporary file
        val tempFile = File.createTempFile("spoof_build_vars", ".txt")
        tempFile.deleteOnExit()

        // Write template directive
        tempFile.writeText("TEMPLATE=pixel7pro")

        // Update Config
        Config.updateBuildVars(tempFile)

        // Verify
        assertEquals("Pixel 7 Pro", Config.getBuildVar("MODEL"))
        assertEquals("google/cheetah/cheetah:14/AP1A.240305.019.A1/11445699:user/release-keys", Config.getBuildVar("FINGERPRINT"))
    }

    @Test
    fun testUpdateBuildVars_withOverride() {
        val tempFile = File.createTempFile("spoof_build_vars_override", ".txt")
        tempFile.deleteOnExit()

        // Template + Override
        tempFile.writeText("TEMPLATE=pixel7pro\nMODEL=My Custom Pixel")

        Config.updateBuildVars(tempFile)

        assertEquals("My Custom Pixel", Config.getBuildVar("MODEL"))
        assertEquals("google", Config.getBuildVar("BRAND"))
    }
}
