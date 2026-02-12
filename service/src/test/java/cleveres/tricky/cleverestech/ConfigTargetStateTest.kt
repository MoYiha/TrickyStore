package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.PackageTrie
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

class ConfigTargetStateTest {

    @Before
    fun setUp() {
        // Reset Config state as much as possible to avoid side effects
        resetConfig()
    }

    @After
    fun tearDown() {
        resetConfig()
    }

    private fun resetConfig() {
        // Reset isTeeBroken, isGlobalMode, etc.
        setPrivateField(Config, "isTeeBrokenMode", false)
        setPrivateField(Config, "isAutoTeeBroken", false)
        setPrivateField(Config, "isGlobalMode", false)

        // Reset packageCache
        val packageCache = getPrivateField(Config, "packageCache") as ConcurrentHashMap<Int, Any>
        packageCache.clear()

        // Reset targetState
        val targetStateClass = Class.forName("cleveres.tricky.cleverestech.Config\$TargetState")
        val constructor = targetStateClass.getDeclaredConstructor(PackageTrie::class.java, PackageTrie::class.java)
        constructor.isAccessible = true
        val emptyState = constructor.newInstance(PackageTrie<Boolean>(), PackageTrie<Boolean>())
        setPrivateField(Config, "targetState", emptyState)
    }

    @Test
    fun testNeedHack_caching() {
        // 1. Setup Mock TargetState with a rule
        val hackTrie = PackageTrie<Boolean>()
        hackTrie.add("com.hack.me", true)
        val genTrie = PackageTrie<Boolean>()

        val targetState = createTargetState(hackTrie, genTrie)
        setPrivateField(Config, "targetState", targetState)

        // 2. Setup Mock PackageCache for UID 1001 -> "com.hack.me"
        mockPackage(1001, arrayOf("com.hack.me"))

        // 3. First call - should calculate and cache
        assertTrue("needHack should return true for com.hack.me", Config.needHack(1001))

        // 4. Verify Cache
        val hackCache = getFieldFromTargetState(targetState, "hackCache") as ConcurrentHashMap<Int, Boolean>
        assertTrue("Cache should contain entry for 1001", hackCache.containsKey(1001))
        assertTrue("Cache value should be true", hackCache[1001] == true)

        // 5. Verify caching avoids re-calculation (implicitly tested by cache presence)
        // If we clear package cache but keep hack cache, it should still work?
        // Wait, packageCache is used by checkPackages.
        // If we remove from packageCache, checkPackages would fail/return false (empty array).
        // But if hackCache works, it won't call checkPackages.

        val packageCache = getPrivateField(Config, "packageCache") as ConcurrentHashMap<Int, Any>
        packageCache.remove(1001)

        // Now Config.getPackages(1001) would return empty array (or try to fetch from PM which fails in test environment)
        // But needHack should return cached value.
        assertTrue("needHack should return cached true even if package info is gone", Config.needHack(1001))
    }

    @Test
    fun testNeedGenerate_caching() {
        val hackTrie = PackageTrie<Boolean>()
        val genTrie = PackageTrie<Boolean>()
        genTrie.add("com.gen.me", true)

        val targetState = createTargetState(hackTrie, genTrie)
        setPrivateField(Config, "targetState", targetState)

        mockPackage(2001, arrayOf("com.gen.me"))

        assertTrue("needGenerate should return true for com.gen.me", Config.needGenerate(2001))

        val genCache = getFieldFromTargetState(targetState, "generateCache") as ConcurrentHashMap<Int, Boolean>
        assertTrue("Cache should contain entry for 2001", genCache.containsKey(2001))
        assertTrue("Cache value should be true", genCache[2001] == true)
    }

    @Test
    fun testNeedGenerate_withTeeBroken() {
        // When TEE is broken, needGenerate checks BOTH generatePackages AND hackPackages.

        val hackTrie = PackageTrie<Boolean>()
        hackTrie.add("com.hack.me", true)
        val genTrie = PackageTrie<Boolean>()
        // genTrie is empty

        val targetState = createTargetState(hackTrie, genTrie)
        setPrivateField(Config, "targetState", targetState)

        // Set TEE broken
        Config.setTeeBroken(true)

        mockPackage(3001, arrayOf("com.hack.me"))

        // needGenerate should return true because TEE broken falls back to hack packages
        assertTrue("needGenerate should return true when TEE broken and app is in hack list", Config.needGenerate(3001))

        // Verify caches
        val genCache = getFieldFromTargetState(targetState, "generateCache") as ConcurrentHashMap<Int, Boolean>
        val hackCache = getFieldFromTargetState(targetState, "hackCache") as ConcurrentHashMap<Int, Boolean>

        assertTrue("Gen Cache should act and store false", genCache.containsKey(3001) && genCache[3001] == false)
        assertTrue("Hack Cache should act and store true", hackCache.containsKey(3001) && hackCache[3001] == true)
    }

    // Helper methods using reflection

    private fun createTargetState(hack: PackageTrie<Boolean>, gen: PackageTrie<Boolean>): Any {
        val clazz = Class.forName("cleveres.tricky.cleverestech.Config\$TargetState")
        val constructor = clazz.getDeclaredConstructor(PackageTrie::class.java, PackageTrie::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(hack, gen)
    }

    private fun getFieldFromTargetState(instance: Any, fieldName: String): Any {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(instance)
    }

    private fun setPrivateField(instance: Any, fieldName: String, value: Any?) {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(instance, value)
    }

    private fun getPrivateField(instance: Any, fieldName: String): Any? {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(instance)
    }

    private fun mockPackage(uid: Int, packages: Array<String>) {
        val packageCache = getPrivateField(Config, "packageCache") as ConcurrentHashMap<Int, Any>

        // Config.CachedPackage is internal, so we can access it?
        // Or use reflection if it fails.
        // Let's try reflection for safety since it is inside Config object

        val cachedPackageClass = Class.forName("cleveres.tricky.cleverestech.Config\$CachedPackage")
        val constructor = cachedPackageClass.getDeclaredConstructor(Array<String>::class.java, Long::class.javaPrimitiveType)
        constructor.isAccessible = true
        val cachedPkg = constructor.newInstance(packages, System.currentTimeMillis())

        packageCache[uid] = cachedPkg
    }
}
