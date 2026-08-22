package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Keeps the V2 policy state compatible with early-boot code that still consumes
 * fixed marker files before the Android service is available.
 *
 * The file names are intentionally closed over a fixed allow-list. All paths are
 * checked without following links before any mutation so a malformed or replaced
 * marker fails closed instead of turning a V2 policy update into an arbitrary file
 * write/delete primitive.
 */
internal object LegacyIdentityMarkers {
    const val ENGINE = "spoof_enabled"
    const val BUILD = "spoof_build_identity"
    const val TELEPHONY = "telephony"
    const val REGION = "spoof_region_cn"
    const val REFRESH = "random_on_boot"

    private const val ROOT_ONLY_MODE = 384 // 0600

    data class DesiredState(
        val engine: Boolean,
        val build: Boolean,
        val telephony: Boolean,
        val region: Boolean,
        val refresh: Boolean,
    )

    internal data class Operation(
        val name: String,
        val file: File,
        val enabled: Boolean,
    )

    private val allowedNames = setOf(ENGINE, BUILD, TELEPHONY, REGION, REFRESH)

    fun plan(root: File, desired: DesiredState): List<Operation> {
        val expected =
            linkedMapOf(
                ENGINE to desired.engine,
                BUILD to desired.build,
                TELEPHONY to desired.telephony,
                REGION to desired.region,
                REFRESH to desired.refresh,
            )
        val result = ArrayList<Operation>(expected.size)
        for ((name, enabled) in expected) {
            require(name in allowedNames) { "Unsupported identity marker" }
            val file = File(root, name)
            val exists = validatePath(file)
            if (exists != enabled) result += Operation(name, file, enabled)
        }
        return result
    }

    fun apply(operations: List<Operation>) {
        // Revalidate the complete plan immediately before mutating anything. This
        // prevents a pre-existing symlink/directory from causing a partial update.
        operations.forEach { operation ->
            require(operation.name in allowedNames) { "Unsupported identity marker" }
            validatePath(operation.file)
        }
        operations.forEach { operation ->
            if (operation.enabled) {
                SecureFile.touch(operation.file, ROOT_ONLY_MODE)
            } else {
                Files.deleteIfExists(operation.file.toPath())
            }
            // Update in-process legacy caches without waiting for the file observer.
            Config.refreshRuntimeSetting(operation.name)
        }
    }

    private fun validatePath(file: File): Boolean {
        val path = file.toPath()
        val exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS)
        if (exists && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Identity compatibility marker is not a regular file: ${file.name}")
        }
        return exists
    }
}
