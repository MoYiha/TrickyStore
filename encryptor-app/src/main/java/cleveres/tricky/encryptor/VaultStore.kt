package cleveres.tricky.encryptor

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

internal object VaultStore {
    private const val VAULT_DIR = "vault"
    private const val MAX_FILES = 256
    private const val MAX_CBOX_BYTES = 10 * 1024 * 1024 + 64 * 1024

    fun directory(context: Context): File {
        val root = context.noBackupFilesDir
        val vault = File(root, VAULT_DIR)
        require(vault.isDirectory || vault.mkdir()) { "Secure vault is unavailable" }
        require(!Files.isSymbolicLink(vault.toPath())) { "Secure vault path is invalid" }
        return vault
    }

    fun filenameFor(author: String): String {
        val safe = author.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('.').take(100)
        return "${safe.ifEmpty { "keybox" }}.cbox"
    }

    fun list(context: Context): List<File> {
        val vault = directory(context)
        val files = ArrayList<File>(MAX_FILES)
        Files.newDirectoryStream(vault.toPath()).use { entries ->
            for (entry in entries) {
                if (files.size == MAX_FILES) break
                if (!entry.fileName.toString().endsWith(".cbox", ignoreCase = true)) continue
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue
                val file = entry.toFile()
                if (file.length() !in 1..MAX_CBOX_BYTES.toLong()) continue
                files += file
            }
        }
        return files.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name.lowercase() })
    }

    fun delete(file: File): Boolean {
        if (!file.name.endsWith(".cbox", ignoreCase = true)) return false
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return false
        return Files.deleteIfExists(file.toPath())
    }

    fun export(file: File, output: OutputStream) {
        require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Vault entry is invalid" }
        val expected = file.length()
        require(expected in 1..MAX_CBOX_BYTES.toLong()) { "Vault entry exceeds the size limit" }
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    copied += count
                    if (copied > expected || copied > MAX_CBOX_BYTES) {
                        throw IOException("Vault entry changed while exporting")
                    }
                    output.write(buffer, 0, count)
                }
                if (copied != expected) throw IOException("Vault entry changed while exporting")
            } finally {
                buffer.fill(0)
            }
        }
    }

    /** Moves legacy app-specific external ciphertext into the no-backup private vault. */
    fun migrateLegacy(context: Context) {
        val legacy = context.getExternalFilesDir(null) ?: return
        if (!Files.isDirectory(legacy.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        val vault = directory(context)
        var migrated = 0
        Files.newDirectoryStream(legacy.toPath()).use { entries ->
            for (entry in entries) {
                if (migrated == MAX_FILES) break
                val name = entry.fileName.toString()
                if (!name.endsWith(".cbox", ignoreCase = true)) continue
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue
                val size = Files.size(entry)
                if (size !in 1..MAX_CBOX_BYTES.toLong()) continue
                val target = File(vault, name).toPath()
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue
                runCatching {
                    Files.move(entry, target, StandardCopyOption.ATOMIC_MOVE)
                }.recoverCatching {
                    Files.copy(entry, target)
                    Files.delete(entry)
                }.onSuccess {
                    migrated++
                }
            }
        }
    }
}
