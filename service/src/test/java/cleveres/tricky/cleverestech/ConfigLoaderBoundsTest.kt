package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.PackageTrie
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ConfigLoaderBoundsTest {
    private lateinit var root: File

    @Before
    fun setup() {
        Config.reset()
        root = Files.createTempDirectory("config-loader-bounds").toFile()
        Config.setRootForTesting(root)
    }

    @After
    fun tearDown() {
        Config.reset()
        root.deleteRecursively()
    }

    private fun invokeLoader(
        name: String,
        file: File,
    ) {
        val method =
            Config::class.java.declaredMethods.first {
                it.name.startsWith(name) && it.parameterCount == 1
            }
        method.isAccessible = true
        method.invoke(Config, file)
    }

    @Suppress("UNCHECKED_CAST")
    private fun targetPackages(): PackageTrie<Boolean> {
        val stateField = Config::class.java.getDeclaredField("targetState")
        stateField.isAccessible = true
        val state = stateField.get(Config)
        val packagesField = state.javaClass.getDeclaredField("hackPackages")
        packagesField.isAccessible = true
        return packagesField.get(state) as PackageTrie<Boolean>
    }

    @Suppress("UNCHECKED_CAST")
    private fun appConfigs(): PackageTrie<Config.AppSpoofConfig> {
        val stateField = Config::class.java.getDeclaredField("appConfigState")
        stateField.isAccessible = true
        val state = stateField.get(Config)
        val configsField = state.javaClass.getDeclaredField("configs")
        configsField.isAccessible = true
        return configsField.get(state) as PackageTrie<Config.AppSpoofConfig>
    }

    @Test
    fun `target loader rejects rules beyond its bound without replacing state`() {
        val valid = File(root, "target.txt")
        valid.writeText("com.example.keep\n")
        invokeLoader("updateTargetPackages", valid)
        assertTrue(targetPackages().matches("com.example.keep"))

        valid.bufferedWriter().use { writer ->
            repeat(2049) { index -> writer.appendLine("com.example.app$index") }
        }
        invokeLoader("updateTargetPackages", valid)

        val packages = targetPackages()
        assertEquals(1, packages.size)
        assertTrue(packages.matches("com.example.keep"))
    }

    @Test
    fun `app config loader rejects rules beyond its bound without replacing state`() {
        val config = File(root, "app_config")
        config.writeText("com.example.keep null keybox.xml\n")
        invokeLoader("updateAppConfigs", config)
        assertEquals("keybox.xml", appConfigs().get("com.example.keep")?.keyboxFilename)

        config.bufferedWriter().use { writer ->
            repeat(1025) { index -> writer.appendLine("com.example.app$index null keybox.xml") }
        }
        invokeLoader("updateAppConfigs", config)

        val configs = appConfigs()
        assertEquals(1, configs.size)
        assertEquals("keybox.xml", configs.get("com.example.keep")?.keyboxFilename)
        assertNull(configs.get("com.example.app0"))
    }
}
