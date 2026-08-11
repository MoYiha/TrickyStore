package cleveres.tricky.cleverestech

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ConfigModuleHashFileTest {
    private lateinit var root: File
    private var previousHash: ByteArray? = null
    private var previousVarsHash: ByteArray? = null

    @Before
    fun setup() {
        root = Files.createTempDirectory("module-hash-test").toFile()
        val field = Config::class.java.getDeclaredField("moduleHash")
        field.isAccessible = true
        previousHash = (field.get(Config) as ByteArray?)?.clone()
        val varsField = Config::class.java.getDeclaredField("moduleHashFromVars")
        varsField.isAccessible = true
        previousVarsHash = (varsField.get(Config) as ByteArray?)?.clone()
        field.set(Config, null)
        varsField.set(Config, null)
    }

    @After
    fun tearDown() {
        val field = Config::class.java.getDeclaredField("moduleHash")
        field.isAccessible = true
        field.set(Config, previousHash)
        val varsField = Config::class.java.getDeclaredField("moduleHashFromVars")
        varsField.isAccessible = true
        varsField.set(Config, previousVarsHash)
        root.deleteRecursively()
    }

    private fun updateModuleHash(file: File?) {
        val method =
            Config::class.java.declaredMethods.single {
                it.name.startsWith("updateModuleHash-") &&
                    it.parameterTypes.contentEquals(arrayOf(File::class.java))
            }
        method.isAccessible = true
        method.invoke(Config, file)
    }

    @Test
    fun `loads one bounded sha256 digest`() {
        val file = File(root, "module_hash")
        file.writeText("00".repeat(32) + "\n")

        updateModuleHash(file)

        assertArrayEquals(ByteArray(32), Config.getModuleHash())
    }

    @Test
    fun `rejects oversized module hash before decoding`() {
        val file = File(root, "module_hash")
        file.writeText("a".repeat(129))

        updateModuleHash(file)

        assertNull(Config.getModuleHash())
    }

    @Test
    fun `rejects symbolic link module hash`() {
        val target = File(root, "target").apply { writeText("00".repeat(32)) }
        val link = File(root, "module_hash")
        Files.createSymbolicLink(link.toPath(), target.toPath())

        updateModuleHash(link)

        assertNull(Config.getModuleHash())
    }
}
