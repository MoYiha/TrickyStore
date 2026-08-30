package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Versioned package enumeration for the privileged service and Android platform-contract tests.
 *
 * Production normally uses the hidden IPackageManager Binder API. Android instrumentation runs in
 * the application hidden-API domain, where Android 17 can deny linking even when the runtime ABI is
 * known. In that case we fall back to Android's fixed `cmd package list packages` interface. The
 * fallback never invokes a shell, accepts only the integer user id, and bounds time and output.
 */
internal object InstalledPackagesCompat {
    private const val COMMAND_TIMEOUT_MS = 3_000L
    private const val PROCESS_EXIT_GRACE_MS = 250L
    private const val MAX_COMMAND_OUTPUT_BYTES = 1024 * 1024
    private const val MAX_COMMAND_PACKAGES = 100_000
    private const val PACKAGE_PREFIX = "package:"
    private val packageNamePattern = Regex("[A-Za-z0-9_.]{1,255}")

    fun getInstalledPackageNames(
        packageManager: IPackageManager,
        userId: Int,
    ): List<String> =
        try {
            getInstalledPackageNamesViaBinder(packageManager, userId)
        } catch (error: LinkageError) {
            Logger.i("Hidden PackageManager package enumeration is unavailable; using bounded cmd fallback")
            getInstalledPackageNamesViaCommand(userId)
        } catch (error: SecurityException) {
            Logger.i("Hidden PackageManager package enumeration was denied; using bounded cmd fallback")
            getInstalledPackageNamesViaCommand(userId)
        }

    private fun getInstalledPackageNamesViaBinder(
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

    private fun getInstalledPackageNamesViaCommand(userId: Int): List<String> {
        require(userId >= 0) { "Package-list user id must be non-negative" }
        val process =
            ProcessBuilder(
                "/system/bin/cmd",
                "package",
                "list",
                "packages",
                "--user",
                userId.toString(),
            ).redirectErrorStream(true).start()

        val reader =
            FutureTask {
                val packages = parsePackageListStream(process.inputStream)
                if (!process.waitFor(PROCESS_EXIT_GRACE_MS, TimeUnit.MILLISECONDS)) {
                    throw IOException("Package-list command did not terminate after closing its output")
                }
                if (process.exitValue() != 0) {
                    throw IOException("Package-list command failed with exit code ${process.exitValue()}")
                }
                packages
            }
        Thread(reader, "ct-package-list").apply {
            isDaemon = true
            start()
        }

        try {
            return reader.get(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            throw IOException("Package-list command timed out", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Package-list command was interrupted", error)
        } catch (error: ExecutionException) {
            val cause = error.cause
            if (cause is IOException) throw cause
            throw IOException("Package-list command failed", cause)
        } finally {
            reader.cancel(true)
            process.destroyForcibly()
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun parsePackageListStream(input: java.io.InputStream): List<String> {
        val packages = ArrayList<String>()
        var totalBytes = 0
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (rawLine in lines) {
                totalBytes += rawLine.length + 1
                if (totalBytes > MAX_COMMAND_OUTPUT_BYTES) {
                    throw IOException("Package-list command output exceeds its size limit")
                }
                val line = rawLine.trim()
                if (!line.startsWith(PACKAGE_PREFIX)) continue
                val packageName = line.substring(PACKAGE_PREFIX.length)
                if (!packageNamePattern.matches(packageName)) continue
                require(packages.size < MAX_COMMAND_PACKAGES) { "Package-list output contains too many packages" }
                packages += packageName
            }
        }
        return packages
    }

    @androidx.annotation.VisibleForTesting
    internal fun parsePackageListOutput(output: ByteArray): List<String> {
        require(output.size <= MAX_COMMAND_OUTPUT_BYTES) { "Package-list output exceeds its size limit" }
        return parsePackageListStream(output.inputStream())
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() = Unit
}
