package cleveres.tricky.cleverestech.util

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import cleveres.tricky.cleverestech.Logger

interface SecureFileOperations {
    fun writeText(file: File, content: String)
}

object SecureFile {
    var impl: SecureFileOperations = DefaultSecureFileOperations()

    fun writeText(file: File, content: String) {
        impl.writeText(file, content)
    }

    class DefaultSecureFileOperations : SecureFileOperations {
        override fun writeText(file: File, content: String) {
            val path = file.absolutePath
            var fd: FileDescriptor? = null
            try {
                // 384 decimal is 0600 octal (rw-------)
                val mode = 384
                val flags = OsConstants.O_CREAT or OsConstants.O_TRUNC or OsConstants.O_WRONLY
                fd = Os.open(path, flags, mode)

                // Ensure permissions are set correctly even if file already existed
                Os.fchmod(fd, mode)

                val bytes = content.toByteArray(Charsets.UTF_8)
                var bytesWritten = 0
                while (bytesWritten < bytes.size) {
                    val w = Os.write(fd, bytes, bytesWritten, bytes.size - bytesWritten)
                    if (w <= 0) break // Should not happen unless error
                    bytesWritten += w
                }
            } catch (e: Exception) {
                Logger.e("SecureFile: Failed to write to $path", e)
                throw e
            } finally {
                if (fd != null) {
                    try { Os.close(fd) } catch (e: Exception) {}
                }
            }
        }
    }
}
