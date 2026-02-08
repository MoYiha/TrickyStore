package cleveres.tricky.cleverestech.util

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import cleveres.tricky.cleverestech.Logger

interface SecureFileOperations {
    fun writeText(file: File, content: String)
    fun mkdirs(file: File, mode: Int)
    fun touch(file: File, mode: Int)
}

object SecureFile {
    var impl: SecureFileOperations = DefaultSecureFileOperations()

    fun writeText(file: File, content: String) {
        impl.writeText(file, content)
    }

    fun mkdirs(file: File, mode: Int) {
        impl.mkdirs(file, mode)
    }

    fun touch(file: File, mode: Int) {
        impl.touch(file, mode)
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

        override fun mkdirs(file: File, mode: Int) {
            if (file.exists()) {
                if (file.isDirectory) {
                    try { Os.chmod(file.absolutePath, mode) } catch(e: Exception) {}
                }
                return
            }
            // Ensure parent exists
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                mkdirs(parent, mode)
            }
            // Create directory
            try {
                Os.mkdir(file.absolutePath, mode)
                // Enforce again just in case umask messed it up
                Os.chmod(file.absolutePath, mode)
            } catch (e: Exception) {
                // Check if it was created by another thread/process in the meantime
                if (!file.exists()) {
                    Logger.e("SecureFile: Failed to mkdirs $file", e)
                    throw e
                } else {
                     try { Os.chmod(file.absolutePath, mode) } catch(t: Throwable) {}
                }
            }
        }

        override fun touch(file: File, mode: Int) {
            val path = file.absolutePath
            var fd: FileDescriptor? = null
            try {
                val flags = OsConstants.O_CREAT or OsConstants.O_WRONLY
                fd = Os.open(path, flags, mode)
                Os.fchmod(fd, mode)
            } catch (e: Exception) {
                Logger.e("SecureFile: Failed to touch $path", e)
                throw e
            } finally {
                if (fd != null) {
                    try { Os.close(fd) } catch (e: Exception) {}
                }
            }
        }
    }
}
