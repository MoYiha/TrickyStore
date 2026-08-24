package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigResetTest {
    @Test
    fun testResetClearsDynamicPatchCache() {
        val field = Config::class.java.getDeclaredField("dynamicPatchCache")
        field.isAccessible = true
        val cache = requireNotNull(field.get(Config))
        val pollutedKey = "2023-12-05"
        val pollutedValue = System.currentTimeMillis() to 202401
        cache.javaClass.getMethod("put", Any::class.java, Any::class.java).invoke(cache, pollutedKey, pollutedValue)

        val cached = cache.javaClass.getMethod("get", Any::class.java).invoke(cache, pollutedKey) as Pair<*, *>
        assertEquals(202401, cached.second)

        Config.reset()

        val cacheAfterReset = requireNotNull(field.get(Config))
        val size = cacheAfterReset.javaClass.getMethod("size").invoke(cacheAfterReset) as Int
        assertEquals(0, size)
    }

    @Test
    fun testResetDefaultGlobalModes() {
        val globalModeField = Config::class.java.getDeclaredField("isGlobalMode")
        globalModeField.isAccessible = true
        globalModeField.set(Config, true)

        val globalIdentityField = Config::class.java.getDeclaredField("isGlobalIdentityMode")
        globalIdentityField.isAccessible = true
        globalIdentityField.set(Config, true)

        Config.reset()

        org.junit.Assert.assertFalse("Reset must reset isGlobalMode to false", Config.isGlobalMode)
        org.junit.Assert.assertFalse("Reset must reset isGlobalIdentityMode to false", Config.isGlobalIdentityMode)
    }
}
