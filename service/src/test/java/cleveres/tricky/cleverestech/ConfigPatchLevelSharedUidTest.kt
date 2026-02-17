package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class ConfigPatchLevelSharedUidTest {

    @Test
    fun testPatchLevelSharedUid() {
        // Reset Config state
        val packageCacheField = Config::class.java.getDeclaredField("packageCache")
        packageCacheField.isAccessible = true
        (packageCacheField.get(Config) as ConcurrentHashMap<*, *>).clear()

        val securityPatchField = Config::class.java.getDeclaredField("securityPatch")
        securityPatchField.isAccessible = true
        // Set securityPatch map directly
        val testPatchMap = mapOf("com.example.pkgB" to "2023-01-01")
        securityPatchField.set(Config, testPatchMap)

        val defaultPatchField = Config::class.java.getDeclaredField("defaultSecurityPatch")
        defaultPatchField.isAccessible = true
        defaultPatchField.set(Config, "2024-01-01") // Set default

        // Mock packages for UID 1001: [com.example.pkgA, com.example.pkgB]
        val packages = arrayOf("com.example.pkgA", "com.example.pkgB")

        // Mock CachedPackage
        val cachedPackageClass = Class.forName("cleveres.tricky.cleverestech.Config\$CachedPackage")
        val constructor = cachedPackageClass.getDeclaredConstructor(Array<String>::class.java, Long::class.javaPrimitiveType)
        constructor.isAccessible = true
        val cachedPkg = constructor.newInstance(packages, System.currentTimeMillis())

        val packageCache = packageCacheField.get(Config) as ConcurrentHashMap<Int, Any>
        packageCache[1001] = cachedPkg

        // Execute
        val level = Config.getPatchLevel(1001)

        // Expected: 202301 (from pkgB)
        // Actual (Bug): 202401 (default, because it only checks pkgA)
        assertEquals("Should use specific patch level if ANY package in UID matches", 202301, level)
    }
}
