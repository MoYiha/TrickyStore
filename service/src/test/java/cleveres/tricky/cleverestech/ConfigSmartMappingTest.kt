package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Field

class ConfigSmartMappingTest {

    @Before
    fun setUp() {
        // Reset Config state
        val tempDir = java.nio.file.Files.createTempDirectory("test_config_smart").toFile()
        tempDir.deleteOnExit()

        // Initialize DeviceTemplateManager with built-ins
        DeviceTemplateManager.initialize(tempDir)

        // Force Config to reload templates from Manager
        Config.updateCustomTemplates(null)
    }

    private fun setPackageCache(uid: Int, packages: Array<String>) {
        val field = Config::class.java.getDeclaredField("packageCache")
        field.isAccessible = true
        val cache = field.get(Config) as MutableMap<Int, Array<String>>
        cache[uid] = packages
    }

    private fun setAppConfigs(configs: Map<String, Config.AppSpoofConfig>) {
        val field = Config::class.java.getDeclaredField("appConfigs")
        field.isAccessible = true
        field.set(Config, configs)
    }

    @Test
    fun testSmartPropertyMapping() {
        val testUid = 12345
        val testPackage = "com.test.app"
        val templateName = "pixel8pro"

        // 1. Setup Mock State
        setPackageCache(testUid, arrayOf(testPackage))
        setAppConfigs(mapOf(testPackage to Config.AppSpoofConfig(templateName, null)))

        // 2. Verify Mapping
        // ro.product.model -> MODEL
        assertEquals("Pixel 8 Pro", Config.getBuildVar("ro.product.model", testUid))
        assertEquals("Pixel 8 Pro", Config.getBuildVar("ro.product.vendor.model", testUid))

        // ro.build.fingerprint -> FINGERPRINT
        // Note: The actual string depends on the hardcoded template in DeviceTemplateManager.kt.
        val expectedFingerprint = "google/husky/husky:14/AP1A.240405.002/11480754:user/release-keys"
        assertEquals(expectedFingerprint, Config.getBuildVar("ro.build.fingerprint", testUid))
        assertEquals(expectedFingerprint, Config.getBuildVar("ro.vendor.build.fingerprint", testUid))

        // ro.product.brand -> BRAND
        assertEquals("google", Config.getBuildVar("ro.product.brand", testUid))

        // ro.build.version.security_patch -> SECURITY_PATCH
        assertEquals("2024-04-05", Config.getBuildVar("ro.build.version.security_patch", testUid))

        // ID
        assertEquals("AP1A.240405.002", Config.getBuildVar("ro.build.id", testUid))

        // TAGS
        assertEquals("release-keys", Config.getBuildVar("ro.build.tags", testUid))
    }

    @Test
    fun testFallbackToSpoofedProperties() {
        // Test that properties in spoofedProperties (like boot state) are returned even if not in template
        // spoofedProperties has "ro.boot.verifiedbootstate" -> "green"

        // Use a UID with NO config first
        assertEquals("green", Config.getBuildVar("ro.boot.verifiedbootstate", 99999))

        // Use a UID WITH config (should still fall back if template doesn't have it)
        val testUid = 12345
        val testPackage = "com.test.app"
        setPackageCache(testUid, arrayOf(testPackage))
        setAppConfigs(mapOf(testPackage to Config.AppSpoofConfig("pixel8pro", null)))

        // Template "pixel8pro" does NOT have "ro.boot.verifiedbootstate" (it has MODEL, BRAND etc)
        // So it should fall back to global/spoofed
        assertEquals("green", Config.getBuildVar("ro.boot.verifiedbootstate", testUid))
    }
}
