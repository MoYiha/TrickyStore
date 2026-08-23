package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.readUtf8FileSnapshotBounded
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Small common owner for root-only module state.
 *
 * Callers supply a simple file name and an explicit byte bound. The helper rejects symbolic links,
 * non-regular files, unsafe roots, and unbounded reads before delegating atomic writes to SecureFile.
 */
internal object SafeConfigStore {
    private const val ROOT_ONLY_FILE_MODE = 384
    private val safeName = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")

    fun preflight(
        root: File,
        name: String,
    ): File {
        requireSafeRoot(root)
        require(safeName.matches(name)) { "Unsafe config file name" }
        val file = File(root, name)
        val path = file.toPath()
        if (Files.isSymbolicLink(path)) throw IOException("Config file must not be a symbolic link: $name")
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Config file must be regular: $name")
        }
        return file
    }

    fun readText(
        root: File,
        name: String,
        maxBytes: Long,
        minBytes: Long = 0,
    ): String? {
        require(maxBytes >= 0 && minBytes in 0..maxBytes)
        val file = preflight(root, name)
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        return readUtf8FileSnapshotBounded(file, minBytes, maxBytes)
    }

    fun writeText(
        root: File,
        name: String,
        content: String,
        maxBytes: Long,
    ) {
        require(maxBytes >= 0)
        val bytes = content.toByteArray(Charsets.UTF_8)
        try {
            require(bytes.size.toLong() <= maxBytes) { "Config content exceeds the $maxBytes-byte limit" }
            val file = preflight(root, name)
            SecureFile.writeText(file, content)
            preflight(root, name)
        } finally {
            bytes.fill(0)
        }
    }

    fun markerEnabled(
        root: File,
        name: String,
    ): Boolean {
        val file = preflight(root, name)
        return Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
    }

    fun setMarker(
        root: File,
        name: String,
        enabled: Boolean,
    ) {
        val file = preflight(root, name)
        if (enabled) {
            SecureFile.touch(file, ROOT_ONLY_FILE_MODE)
        } else {
            Files.deleteIfExists(file.toPath())
        }
    }

    fun delete(
        root: File,
        name: String,
    ) {
        val file = preflight(root, name)
        Files.deleteIfExists(file.toPath())
    }

    private fun requireSafeRoot(root: File) {
        val path = root.toPath()
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Config root is unsafe")
        }
    }
}
