package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigPatchLevelTest {

    @Test
    fun testGetPatchLevel_usesPackageName() {
        Config.reset()

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
        iPmField.set(Config, mockPm)

        // 3. Inject Security Patch Map
        val securityPatchField = Config::class.java.getDeclaredField("securityPatch")
        securityPatchField.isAccessible = true

        val testPatchMap = mapOf("com.example.patched" to "2023-12-05")
        securityPatchField.set(Config, testPatchMap)

        try {
            // 5. Verify Patch Level
            // 2023-12-05 -> 202312
            val level = Config.getPatchLevel(1002)
            assertEquals(202312, level)

        } finally {
            Config.reset()
        }
    }
}
