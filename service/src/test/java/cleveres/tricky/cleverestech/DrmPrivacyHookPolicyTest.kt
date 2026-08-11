package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DrmPrivacyHookPolicyTest {
    @Test
    fun `DRM hook targets only the byte-array device identity privacy path`() {
        val source = sourceFile("DrmInterceptor.kt").readText()

        assertTrue(source.contains("android.hardware.drm.IDrmFactory"))
        assertTrue(source.contains("android.hardware.drm.IDrmPlugin"))
        assertTrue(source.contains("deviceUniqueId"))
        assertTrue(source.contains("getPropertyByteArray"))
        assertTrue(source.contains("Config.AppPrivacyMode.ISOLATE"))

        assertFalse(source.contains("getPropertyString"))
        assertFalse(source.contains("writeString(\"L1\")"))
        assertFalse(source.contains("\"L2\""))
        assertFalse(source.contains("\"L3\""))
    }

    @Test
    fun `DRM privacy path does not depend on keystore DRM passthrough targeting`() {
        val source = sourceFile("DrmInterceptor.kt").readText()

        assertFalse(source.contains("Config.needHack"))
        assertFalse(source.contains("isDrmPassthroughEnabled"))
        assertTrue(source.contains("getAppPrivacyMode(uid) == Config.AppPrivacyMode.ISOLATE"))
    }

    @Test
    fun `DRM service discovery avoids blocked ServiceManager reflection`() {
        val source = sourceFile("DrmInterceptor.kt").readText()

        assertFalse(source.contains("getDeclaredInstances"))
        assertFalse(source.contains("getServiceDebugInfo"))
        assertTrue(source.contains("ServiceManager.listServices()"))
        assertTrue(source.contains("ProcessBuilder(\"/system/bin/dumpsys\", \"--pid\", serviceName)"))
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
