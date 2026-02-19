package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class ConfigPatchLevelTest {

    @Test
    fun testGetPatchLevel_usesPackageName() {
        // 0. Ensure Package Cache is cleared
        val packageCacheField = Config::class.java.getDeclaredField("packageCache")
        packageCacheField.isAccessible = true
        (packageCacheField.get(Config) as ConcurrentHashMap<*, *>).clear()

        // 1. Mock IPackageManager
        val mockPm = object : IPackageManager {
             override fun getPackagesForUid(uid: Int): Array<String> {
                 if (uid == 1002) return arrayOf("com.example.patched")
                 return emptyArray()
             }
        }

        // 2. Inject Mock PM into Config
        val iPmField = Config::class.java.getDeclaredField("iPm")
        iPmField.isAccessible = true
        val originalPm = iPmField.get(Config)
        iPmField.set(Config, mockPm)

        // 3. Inject Security Patch Map
        val securityPatchField = Config::class.java.getDeclaredField("securityPatch")
        securityPatchField.isAccessible = true
        val originalSecurityPatch = securityPatchField.get(Config)

        val testPatchMap = mapOf("com.example.patched" to "2023-12-05")
        securityPatchField.set(Config, testPatchMap)

        // 4. Inject Default Security Patch (prevent pollution)
        val defaultPatchField = Config::class.java.getDeclaredField("defaultSecurityPatch")
        defaultPatchField.isAccessible = true
        val originalDefaultPatch = defaultPatchField.get(Config)
        defaultPatchField.set(Config, null)

        try {
            // 5. Verify Patch Level
            // 2023-12-05 -> 202312
            val level = Config.getPatchLevel(1002)
            assertEquals(202312, level)

        } finally {
            // Restore
            iPmField.set(Config, originalPm)
            securityPatchField.set(Config, originalSecurityPatch)
            defaultPatchField.set(Config, originalDefaultPatch)
            // Cleanup cache again to be safe
            (packageCacheField.get(Config) as ConcurrentHashMap<*, *>).clear()
        }
    }
}
