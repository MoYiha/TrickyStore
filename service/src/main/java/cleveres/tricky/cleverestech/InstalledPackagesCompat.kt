package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import java.io.IOException
import java.lang.reflect.Method

/**
 * Bounded compatibility adapter for hidden IPackageManager package enumeration.
 *
 * Android 17/API 37 added a device-id integer to this hidden Binder method. Calling the
 * compile-time signature directly is still preferred; this adapter is entered only after a
 * NoSuchMethodError and resolves one of the explicitly supported runtime signatures.
 */
internal object InstalledPackagesCompat {
    private const val DEVICE_ID_DEFAULT = 0
    private const val PARCELED_LIST_SLICE = "android.content.pm.ParceledListSlice"

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

        val getList =
            result.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "getList" && candidate.parameterCount == 0
            } ?: throw IOException("PackageManager result does not expose getList()")
        val packages = getList.invoke(result) as? List<*>
            ?: throw IOException("PackageManager returned an invalid package list")
        return packages.mapNotNull { entry -> (entry as? PackageInfo)?.packageName }
    }

    private fun resolveMethod(packageManager: IPackageManager): Method {
        val candidates =
            (packageManager.javaClass.methods.asSequence() + IPackageManager::class.java.methods.asSequence())
                .filter { method ->
                    method.name == "getInstalledPackages" &&
                        method.returnType.name == PARCELED_LIST_SLICE &&
                        method.parameterTypes.isSupportedSignature()
                }.distinctBy { method -> method.toGenericString() }
                .toList()
        if (candidates.isEmpty()) throw NoSuchMethodError("Unsupported runtime IPackageManager.getInstalledPackages signature")
        return candidates.minWithOrNull(
            compareBy<Method>({ it.parameterTypes.size }, { if (it.parameterTypes[0] == java.lang.Long.TYPE) 0 else 1 }),
        ) ?: throw NoSuchMethodError("Unsupported runtime IPackageManager.getInstalledPackages signature")
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
