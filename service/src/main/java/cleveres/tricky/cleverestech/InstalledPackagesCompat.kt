package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import android.os.Build

/** Versioned hidden-IPackageManager package enumeration without runtime reflection. */
internal object InstalledPackagesCompat {
    fun getInstalledPackageNames(
        packageManager: IPackageManager,
        userId: Int,
    ): List<String> {
        val packages =
            when {
                Build.VERSION.SDK_INT >= 37 -> packageManager.getInstalledPackagesV17(0L, userId).list
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> packageManager.getInstalledPackages(0L, userId).list
                else -> packageManager.getInstalledPackages(0, userId).list
            }
        return packages.mapNotNull { it.packageName }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() = Unit
}
