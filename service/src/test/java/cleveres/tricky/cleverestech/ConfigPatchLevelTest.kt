package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class ConfigPatchLevelTest {

    @Test
    fun testGetPatchLevel_usesPackageName() {
        Config.reset()

        // 1. Mock IPackageManager with Mockito
        val mockPm = Mockito.mock(IPackageManager::class.java)
        Mockito.doReturn(arrayOf("com.example.patched")).`when`(mockPm).getPackagesForUid(1002)

        // 2. Inject Mock PM into Config
        val iPmField = Config::class.java.getDeclaredField("iPm")
        iPmField.isAccessible = true
        iPmField.set(Config, mockPm)

        // 3. Inject Security Patch Map via SecurityPatchState
        val securityPatchStateField = Config::class.java.getDeclaredField("securityPatchState")
        securityPatchStateField.isAccessible = true

        val testPatchMap = mapOf("com.example.patched" to "2023-12-05")

        val stateClass = Config::class.java.declaredClasses.find { it.simpleName == "SecurityPatchState" }
            ?: throw ClassNotFoundException("SecurityPatchState not found")
        val constructor = stateClass.getDeclaredConstructor(Map::class.java, Any::class.java)
        constructor.isAccessible = true
        val state = constructor.newInstance(testPatchMap, null)

        securityPatchStateField.set(Config, state)

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
