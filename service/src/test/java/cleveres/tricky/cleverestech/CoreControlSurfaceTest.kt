package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreControlSurfaceTest {
    @Test
    fun `legacy core protection flags are not WebUI toggle settings`() {
        val source = sourceFile("WebServer.kt").readText()
        val start = source.indexOf("private val WEB_UI_SETTINGS")
        val end = source.indexOf("private val EDITABLE_CONFIG_FILES", start)

        assertTrue("WebUI setting allowlist was not found", start >= 0 && end > start)
        val settings = source.substring(start, end)
        assertTrue("Identity Spoof Engine must remain user controllable", settings.contains("\"spoof_enabled\""))
        assertTrue("Global Mode must remain user controllable", settings.contains("\"global_mode\""))
        assertFalse("TEE safe mode must not be remotely toggleable", settings.contains("\"tee_broken_mode\""))
        assertFalse("Core property hiding must not be remotely toggleable", settings.contains("\"hide_sensitive_props\""))
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
