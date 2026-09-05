package cleveres.tricky.cleverestech

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles integrity violations by disabling the module and rebooting the system.
 * Idempotent: multiple calls only execute the violation response once.
 *
 * Integrity failures remain fail-closed, but the installed module is preserved so a verifier
 * false-positive cannot permanently erase the installation or destroy evidence needed to debug it.
 */
object IntegrityViolationHandler {
    @Volatile
    var isViolated: Boolean = false
        private set

    private val violationOnce = AtomicBoolean(false)

    internal var disableModule: (String) -> Boolean = ::safeDisableModule
    internal var rebootSystem: () -> Unit = ::performReboot
    internal var terminateProcess: (Int) -> Unit = ::defaultTerminateProcess

    const val VIOLATION_MESSAGE = "Module change detected! Module has been disabled and the system is being restarted."

    /**
     * Handles an integrity violation by quarantining the module with its standard disable marker.
     * This function is idempotent: only the first call executes the violation response.
     */
    fun handleViolation(violations: List<String>) {
        if (!violationOnce.compareAndSet(false, true)) return
        isViolated = true
        Logger.e("INTEGRITY VIOLATION DETECTED:")
        violations.forEach { Logger.e("  - $it") }

        val moduleDir = getModuleDir()
        val disabled =
            try {
                disableModule(moduleDir)
            } catch (error: Exception) {
                Logger.e("Module disable failed with exception", error)
                false
            }

        if (!disabled) {
            Logger.e("Module disable failed - terminating runtime without deleting module or rebooting")
            terminateProcess(1)
            return
        }

        Logger.e("Module disabled after integrity violation - preserving files and initiating reboot")
        try {
            rebootSystem()
        } catch (error: Exception) {
            Logger.e("System reboot failed - halting runtime and terminating process", error)
            terminateProcess(1)
        }
    }

    /** Resets the violation state and injectable handlers for testing. */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        isViolated = false
        violationOnce.set(false)
        disableModule = ::safeDisableModule
        rebootSystem = ::performReboot
        terminateProcess = ::defaultTerminateProcess
    }
}

/**
 * Creates the standard module-manager disable marker only for a known CleveresTricky module root.
 * No integrity failure path recursively deletes module files anymore.
 */
private fun safeDisableModule(moduleDir: String): Boolean {
    if (moduleDir.isBlank()) return false
    return try {
        val root = File(moduleDir).absoluteFile.toPath().normalize()
        val allowed = ALLOWED_MODULE_DIRS.any { candidate ->
            root == File(candidate).toPath().toAbsolutePath().normalize()
        }
        if (!allowed) {
            Logger.e("Refusing to disable unexpected module path: $root")
            false
        } else if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            Logger.e("Refusing to disable unsafe module path: $root")
            false
        } else {
            createDisableMarker(root)
        }
    } catch (error: Exception) {
        Logger.e("Failed to create module disable marker", error)
        false
    }
}

/**
 * Creates an empty `disable` marker without following a pre-existing marker symlink.
 * Existing regular markers are accepted so quarantine is idempotent.
 */
@androidx.annotation.VisibleForTesting
internal fun createDisableMarker(root: Path): Boolean {
    val marker = root.resolve(DISABLE_MARKER)
    if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
        return !Files.isSymbolicLink(marker) &&
            Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
    }

    val options =
        setOf<OpenOption>(
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        )
    Files.newByteChannel(marker, options).use { }
    return !Files.isSymbolicLink(marker) &&
        Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
}

/**
 * Initiates a system reboot using /system/bin/reboot, with a fallback to the 'reboot' command.
 */
private fun performReboot() {
    try {
        ProcessBuilder("/system/bin/reboot")
            .redirectErrorStream(true)
            .start()
    } catch (error: Exception) {
        Logger.e("Reboot via /system/bin/reboot failed", error)
        try {
            ProcessBuilder("reboot")
                .redirectErrorStream(true)
                .start()
        } catch (fallbackError: Exception) {
            Logger.e("Reboot via 'reboot' also failed", fallbackError)
            throw fallbackError
        }
    }
}

private fun defaultTerminateProcess(status: Int) {
    try {
        android.os.Process.killProcess(android.os.Process.myPid())
    } catch (_: Throwable) {
    }
    try {
        kotlin.system.exitProcess(status)
    } catch (_: Throwable) {
    }
    Runtime.getRuntime().halt(status)
}

private const val DISABLE_MARKER = "disable"
private val ALLOWED_MODULE_DIRS =
    setOf(
        "/data/adb/modules/cleverestricky",
        "/data/adb/ksu/modules/cleverestricky",
        "/data/adb/ap/modules/cleverestricky",
    )
