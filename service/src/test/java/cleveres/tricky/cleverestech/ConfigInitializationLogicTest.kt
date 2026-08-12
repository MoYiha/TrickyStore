package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ConfigInitializationLogicTest {
    private lateinit var tempDir: File
    private var originalRoot: Any? = null
    private lateinit var originalSecureFileImpl: SecureFileOperations
    private lateinit var originalLoggerImpl: Logger.LogImpl
    private var setupDone = false

    @Before
    fun setup() {
        // Create temp dir
        tempDir = File(System.getProperty("java.io.tmpdir"), "cleveres_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Mock Logger
        originalLoggerImpl = getLoggerImpl()
        Logger.setImpl(
            object : Logger.LogImpl {
                override fun d(
                    tag: String,
                    msg: String,
                ) {}

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
                ) {}
            },
        )

        // Mock SecureFile
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

        // Set Config.root via reflection
        try {
            val rootField = Config::class.java.getDeclaredField("root")
            rootField.isAccessible = true

            // Get original value
            originalRoot = rootField.get(Config)

            // Set new value
            rootField.set(Config, tempDir)
            setupDone = true
        } catch (e: Exception) {
            // no-op
            e.printStackTrace()
            throw e
        }

        // Initialize DeviceTemplateManager
        DeviceTemplateManager.initialize(tempDir)
    }

    @After
    fun tearDown() {
        if (setupDone) {
            // Restore Config.root
            try {
                val rootField = Config::class.java.getDeclaredField("root")
                rootField.isAccessible = true
                rootField.set(Config, originalRoot)
            } catch (e: Exception) {
                // no-op
            }
        }

        // Restore SecureFile
        SecureFile.impl = originalSecureFileImpl

        // Restore Logger
        Logger.setImpl(originalLoggerImpl)

        // Cleanup temp dir
        tempDir.deleteRecursively()
    }

    private fun getLoggerImpl(): Logger.LogImpl {
        val field = Logger::class.java.getDeclaredField("impl")
        field.isAccessible = true
        return field.get(null) as Logger.LogImpl
    }

    @Test
    fun `randomization stages one synchronized snapshot for early boot`() {
        val spoofEnabledFile = File(tempDir, "spoof_enabled")
        spoofEnabledFile.createNewFile()
        Config.refreshRuntimeSetting("spoof_enabled")

        val spoofFile = File(tempDir, "spoof_build_vars")
        spoofFile.writeText("TEMPLATE=pixel8pro\nATTESTATION_ID_IMEI=490154203237518\n")

        Config.updateCustomTemplates(null)

        try {
            callUpdateBuildVars(spoofFile)
        } catch (e: NoSuchMethodException) {
            // no-op
            Config::class.java.declaredMethods.forEach { // no-op }
            throw e
        }

        assertEquals("490154203237518", Config.getBuildVar("ATTESTATION_ID_IMEI"))
        assertEquals("pixel8pro", Config.getBuildVar("TEMPLATE"))

        val randomOnBootFile = File(tempDir, "random_on_boot")
        randomOnBootFile.createNewFile()
        Config.refreshRuntimeSetting("random_on_boot")

        val stagedFile = File(tempDir, "spoof_build_vars.next")
        assertTrue("Next-boot snapshot should be staged", stagedFile.isFile)
        assertEquals("Active snapshot must stay unchanged during this boot", "TEMPLATE=pixel8pro\nATTESTATION_ID_IMEI=490154203237518\n", spoofFile.readText())
        assertEquals("Current attestation snapshot must stay synchronized", "490154203237518", Config.getBuildVar("ATTESTATION_ID_IMEI"))
        assertEquals("Current build snapshot must stay synchronized", "pixel8pro", Config.getBuildVar("TEMPLATE"))

        val stagedContent = stagedFile.readText()
        val nextImei = stagedContent.lineSequence().first { it.startsWith("ATTESTATION_ID_IMEI=") }.substringAfter('=')
        val nextTemplate = stagedContent.lineSequence().first { it.startsWith("TEMPLATE=") }.substringAfter('=')
        assertNotEquals("Next boot should receive a new IMEI", "490154203237518", nextImei)
        assertNotEquals("Next boot should receive a different template", "pixel8pro", nextTemplate)

        Files.move(stagedFile.toPath(), spoofFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        callUpdateBuildVars(spoofFile)
        assertEquals(nextImei, Config.getBuildVar("ATTESTATION_ID_IMEI"))
        assertEquals(nextTemplate, Config.getBuildVar("TEMPLATE"))

        Config.refreshRuntimeSetting("random_on_boot")
        assertTrue("An enabled refresh must keep one pending snapshot", stagedFile.isFile)
        randomOnBootFile.delete()
        Config.refreshRuntimeSetting("random_on_boot")
        assertFalse("Disabling refresh must remove a pending snapshot", stagedFile.exists())
    }

    private fun callUpdateBuildVars(file: File) {
        // Try finding method starting with updateBuildVars
        val methods = Config::class.java.declaredMethods
        val method =
            methods.find { it.name.startsWith("updateBuildVars") }
                ?: throw NoSuchMethodException("updateBuildVars not found")

        method.isAccessible = true
        method.invoke(Config, file)
    }

}
