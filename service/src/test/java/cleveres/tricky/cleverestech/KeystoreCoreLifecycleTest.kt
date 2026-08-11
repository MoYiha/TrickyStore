package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeystoreCoreLifecycleTest {
    @Test
    fun `keystore core lifecycle is independent from identity engine`() {
        val source = sourceFile("KeystoreInterceptor.kt").readText()

        assertTrue(source.contains("fun tryRunKeystoreInterceptor(): Boolean"))
        assertTrue(source.contains("registerBinderInterceptor"))
        assertFalse(
            "Identity Spoof Engine must not stop, park, or skip the core Keystore interceptor",
            source.contains("Config.isSpoofEnabled"),
        )
    }

    private fun sourceFile(name: String): File {
        val relative = "cleveres/tricky/cleverestech/$name"
        return listOf(
            File("src/main/java/$relative"),
            File("service/src/main/java/$relative"),
        ).firstOrNull(File::isFile)
            ?: error("Could not locate $name from ${File(".").absolutePath}")
    }
}
