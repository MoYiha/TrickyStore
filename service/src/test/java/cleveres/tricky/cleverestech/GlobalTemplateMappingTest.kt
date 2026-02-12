package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.SecureFileOperations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Field

class GlobalTemplateMappingTest {

    private lateinit var tempDir: File
    private var originalRoot: Any? = null
    private lateinit var originalSecureFileImpl: SecureFileOperations
    private var setupDone = false

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "cleveres_global_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Mock SecureFile
        originalSecureFileImpl = SecureFile.impl
        SecureFile.impl = object : SecureFileOperations {
            override fun writeText(file: File, content: String) {
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            override fun mkdirs(file: File, mode: Int) {
                file.mkdirs()
            }
            override fun touch(file: File, mode: Int) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
        }

        // Set Config.root
        try {
            val rootField = Config::class.java.getDeclaredField("root")
            rootField.isAccessible = true
            originalRoot = rootField.get(Config)
            rootField.set(Config, tempDir)
            setupDone = true
        } catch (e: Exception) {
            throw e
        }

        // Initialize DeviceTemplateManager with built-ins (so pixel8pro exists)
        DeviceTemplateManager.initialize(tempDir)

        // Clear package cache to avoid side effects from other tests
        val cacheField = Config::class.java.getDeclaredField("packageCache")
        cacheField.isAccessible = true
        (cacheField.get(Config) as MutableMap<*, *>).clear()

        // Force Config to load templates from Manager
        val updateTemplatesMethod = Config::class.java.declaredMethods.find { it.name.startsWith("updateCustomTemplates") }!!
        updateTemplatesMethod.isAccessible = true
        updateTemplatesMethod.invoke(Config, null)
    }

    @After
    fun tearDown() {
        if (setupDone) {
            try {
                val rootField = Config::class.java.getDeclaredField("root")
                rootField.isAccessible = true
                rootField.set(Config, originalRoot)
            } catch (e: Exception) {}
        }
        SecureFile.impl = originalSecureFileImpl
        tempDir.deleteRecursively()
    }

    @Test
    fun testGlobalTemplateMapping() {
        // Write global config with TEMPLATE=pixel8pro
        val spoofFile = File(tempDir, "spoof_build_vars")
        spoofFile.writeText("TEMPLATE=pixel8pro\n")

        // Force update build vars
        val method = Config::class.java.declaredMethods.find { it.name.startsWith("updateBuildVars") }!!
        method.isAccessible = true
        method.invoke(Config, spoofFile)

        // Verify that 'MODEL' is set in buildVars
        // We can't access buildVars directly easily, but we can verify behavior.
        // We know pixel8pro model is "Pixel 8 Pro".

        // Check if ro.product.model is mapped to Pixel 8 Pro
        // Pass a dummy UID that has NO app-specific config
        val model = Config.getBuildVar("ro.product.model", 12345)

        assertEquals("Should map ro.product.model to template MODEL globally", "Pixel 8 Pro", model)
    }
}
