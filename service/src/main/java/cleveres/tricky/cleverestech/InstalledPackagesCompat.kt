package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import java.io.IOException
import java.lang.reflect.Method

/**
 * Bounded compatibility adapter for hidden IPackageManager package enumeration.
 *
 * Android 17/API 37 changed the hidden getInstalledPackages result container while keeping
 * the long-flags/user-id arguments. Hidden API stubs can expose that ABI under a synthetic
 * getInstalledPackagesV17 name at compile time, so resolution deliberately keys on the
 * supported method/parameter shapes and validates the returned container at runtime instead
 * of coupling the bridge to one framework-internal return class name.
 */
internal object InstalledPackagesCompat {
    private const val DEVICE_ID_DEFAULT = 0
    private val supportedMethodNames = setOf("getInstalledPackages", "getInstalledPackagesV17")

    @Volatile
    private var cachedMethod: Method? = null

    fun getInstalledPackageNames(
        packageManager: IPackageManager,
        userId: Int,
    ): List<String> {
        val method = cachedMethod ?: resolveMethod(packageManager).also { cachedMethod = it }
        val parameterTypes = method.parameterTypes
        val flags: Any = if (parameterTypes[0] == java.lang.Long.TYPE) 0L else 0
        val result =
            when (parameterTypes.size) {
                2 -> method.invoke(packageManager, flags, userId)
                3 -> method.invoke(packageManager, flags, DEVICE_ID_DEFAULT, userId)
                else -> throw IOException("Unsupported getInstalledPackages arity")
            } ?: return emptyList()

        val packages = extractPackageList(result)
        return packages.mapNotNull { entry -> (entry as? PackageInfo)?.packageName }
    }

    private fun extractPackageList(result: Any): List<*> {
        if (result is List<*>) return result

        val getter =
            result.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "getList" && candidate.parameterCount == 0
            }
        if (getter != null) {
            val list = getter.invoke(result)
            if (list is List<*>) return list
        }

        val publicField = result.javaClass.fields.firstOrNull { field -> field.name == "list" }
        if (publicField != null) {
            val list = publicField.get(result)
            if (list is List<*>) return list
        }

        val declaredField = result.javaClass.declaredFields.firstOrNull { field -> field.name == "list" }
        if (declaredField != null) {
            val list =
                runCatching {
                    declaredField.isAccessible = true
                    declaredField.get(result)
                }.getOrNull()
            if (list is List<*>) return list
        }

        throw IOException("Unsupported PackageManager result container: ${result.javaClass.name}")
    }

    private fun resolveMethod(packageManager: IPackageManager): Method {
        val candidates =
            (packageManager.javaClass.methods.asSequence() + IPackageManager::class.java.methods.asSequence())
                .filter { method ->
                    method.name in supportedMethodNames && method.parameterTypes.isSupportedSignature()
                }.distinctBy { method -> method.toGenericString() }
                .toList()
        if (candidates.isEmpty()) {
            throw NoSuchMethodError("Unsupported runtime IPackageManager package-enumeration signature")
        }
        return candidates.minWithOrNull(
            compareBy<Method>(
                { if (it.name == "getInstalledPackagesV17") 0 else 1 },
                { it.parameterTypes.size },
                { if (it.parameterTypes[0] == java.lang.Long.TYPE) 0 else 1 },
            ),
        ) ?: throw NoSuchMethodError("Unsupported runtime IPackageManager package-enumeration signature")
    }

    private fun Array<Class<*>>.isSupportedSignature(): Boolean {
        if (size !in 2..3) return false
        if (this[0] != java.lang.Long.TYPE && this[0] != Integer.TYPE) return false
        return drop(1).all { type -> type == Integer.TYPE }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        cachedMethod = null
    }
}