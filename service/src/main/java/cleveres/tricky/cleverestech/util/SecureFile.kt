package cleveres.tricky.cleverestech.util

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.InputStream
import cleveres.tricky.cleverestech.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface SecureFileOperations {
    fun writeText(file: File, content: String)
    fun writeBytes(file: File, content: ByteArray) {
        throw UnsupportedOperationException("writeBytes not implemented in mock")
    }
    fun writeStream(file: File, inputStream: InputStream, limit: Long = -1L) {}
    fun mkdirs(file: File, mode: Int)
    fun touch(file: File, mode: Int)
}

object SecureFile {
    var impl: SecureFileOperations = DefaultSecureFileOperations()
    private val lock = ReentrantLock()

    fun writeText(file: File, content: String) {
        lock.withLock {
            impl.writeText(file, content)
        }
    }

    fun writeBytes(file: File, content: ByteArray) {
        lock.withLock {
            impl.writeBytes(file, content)
        }
    }

    fun writeStream(file: File, inputStream: InputStream, limit: Long = -1L) {
        lock.withLock {
            impl.writeStream(file, inputStream, limit)
        }
    }

    fun mkdirs(file: File, mode: Int) {
        lock.withLock {
            impl.mkdirs(file, mode)
        }
    }

    fun touch(file: File, mode: Int) {
        lock.withLock {
            impl.touch(file, mode)
        }
    }

    class DefaultSecureFileOperations : SecureFileOperations {
        override fun writeText(file: File, content: String) {
            writeBytes(file, content.toByteArray(Charsets.UTF_8))
        }

        override fun writeBytes(file: File, bytes: ByteArray) {
            val path = file.absolutePath
            val tmpPath = "$path.tmp"
            var fd: FileDescriptor? = null
            try {
                // 384 decimal is 0600 octal (rw-------)
                val mode = 384
                val flags = OsConstants.O_CREAT or OsConstants.O_TRUNC or OsConstants.O_WRONLY
                fd = Os.open(tmpPath, flags, mode)

                // Ensure permissions are set correctly
                Os.fchmod(fd, mode)

                var bytesWritten = 0
                while (bytesWritten < bytes.size) {
                    val w = Os.write(fd, bytes, bytesWritten, bytes.size - bytesWritten)
                    if (w <= 0) break // Should not happen unless error
                    bytesWritten += w
                }

                // Sync to verify write
                try { Os.fsync(fd) } catch(e: Exception) {}

                // Close before rename
                try { Os.close(fd); fd = null } catch(e: Exception) {}

                // Atomic rename
                Os.rename(tmpPath, path)

            } catch (e: Exception) {
                Logger.e("SecureFile: Failed to write to $path", e)
                try { Os.remove(tmpPath) } catch(t: Throwable) {}
                throw e
            } finally {
                if (fd != null) {
                    try { Os.close(fd) } catch (e: Exception) {}
                }
            }
        }

        override fun writeStream(file: File, inputStream: InputStream, limit: Long) {
            val path = file.absolutePath
            val tmpPath = "$path.tmp"
            var fd: FileDescriptor? = null
            try {
                // 384 decimal is 0600 octal (rw-------)
                val mode = 384
                val flags = OsConstants.O_CREAT or OsConstants.O_TRUNC or OsConstants.O_WRONLY
                fd = Os.open(tmpPath, flags, mode)

                // Ensure permissions are set correctly
                Os.fchmod(fd, mode)

                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                var totalBytesWritten: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (limit > 0 && totalBytesWritten + bytesRead > limit) {
                        throw java.io.IOException("File size exceeds limit of $limit bytes")
                    }

                    var chunkWritten = 0
                    while (chunkWritten < bytesRead) {
                        val w = Os.write(fd, buffer, chunkWritten, bytesRead - chunkWritten)
                        if (w <= 0) break // Should not happen unless error
                        chunkWritten += w
                    }
                    totalBytesWritten += bytesRead
                }

                // Sync to verify write
                try { Os.fsync(fd) } catch(e: Exception) {}

                // Close before rename
                try { Os.close(fd); fd = null } catch(e: Exception) {}

                // Atomic rename
                Os.rename(tmpPath, path)

            } catch (e: Exception) {
                Logger.e("SecureFile: Failed to write stream to $path", e)
                try { Os.remove(tmpPath) } catch(t: Throwable) {}
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
