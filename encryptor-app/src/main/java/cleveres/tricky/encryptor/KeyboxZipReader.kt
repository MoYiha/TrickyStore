package cleveres.tricky.encryptor

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

internal object KeyboxImportReader {
    fun process(
        input: InputStream,
        displayName: String,
        validateXml: (ByteArray) -> Boolean,
        onKeybox: (displayName: String, bytes: ByteArray) -> Unit,
    ): Int {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()

        return if (first == 'P'.code && second == 'K'.code) {
            KeyboxZipReader.process(buffered, validateXml, onKeybox)
        } else {
            processSingleXml(buffered, displayName, validateXml, onKeybox)
        }
    }

    private fun processSingleXml(
        input: InputStream,
        displayName: String,
        validateXml: (ByteArray) -> Boolean,
        onKeybox: (displayName: String, bytes: ByteArray) -> Unit,
    ): Int {
        val bytes = readBoundedBytes(input, KeyboxZipReader.MAX_XML_BYTES, "XML file exceeds 10 MiB")
        try {
            if (bytes.isEmpty()) throw IOException("XML file is empty")
            if (!validateXml(bytes)) throw IOException("Selected keybox XML is invalid")
            onKeybox(safeDisplayName(displayName), bytes)
            return 1
        } finally {
            bytes.fill(0)
        }
    }
}

internal object KeyboxZipReader {
    internal const val MAX_KEYBOX_FILES = 10_000
    internal const val MAX_XML_BYTES = 10 * 1024 * 1024
    internal const val MAX_ARCHIVE_ENTRIES = 20_000
    private const val MAX_ENTRY_NAME_CHARS = 1024
    private const val MAX_IGNORED_ENTRY_BYTES = 1024 * 1024
    private const val MAX_TOTAL_IGNORED_BYTES = 16 * 1024 * 1024

    fun process(
        input: InputStream,
        validateXml: (ByteArray) -> Boolean,
        onKeybox: (displayName: String, bytes: ByteArray) -> Unit,
    ): Int {
        var processed = 0
        var archiveEntries = 0
        var totalIgnoredBytes = 0

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                archiveEntries++
                if (archiveEntries > MAX_ARCHIVE_ENTRIES) throw IOException("ZIP contains too many entries")
                validateEntryHeader(entry)

                if (entry.isDirectory || !entry.name.endsWith(".xml", ignoreCase = true)) {
                    val ignoredBytes = drainIgnoredEntry(zip)
                    if (ignoredBytes > MAX_TOTAL_IGNORED_BYTES - totalIgnoredBytes) {
                        throw IOException("ZIP contains too much unrelated content")
                    }
                    totalIgnoredBytes += ignoredBytes
                    zip.closeEntry()
                    continue
                }

                if (processed >= MAX_KEYBOX_FILES) throw IOException("ZIP contains more than 10000 keybox XML files")
                if (entry.size > MAX_XML_BYTES) throw IOException("XML file exceeds 10 MiB")

                val bytes = readBoundedBytes(zip, MAX_XML_BYTES, "XML file exceeds 10 MiB")
                try {
                    if (bytes.isEmpty()) throw IOException("XML file is empty")
                    if (!validateXml(bytes)) throw IOException("ZIP contains an invalid keybox XML")
                    onKeybox(safeDisplayName(entry.name), bytes)
                    processed++
                } finally {
                    bytes.fill(0)
                }
                zip.closeEntry()
            }
        }

        if (processed == 0) throw IOException("ZIP does not contain keybox XML files")
        return processed
    }

    private fun validateEntryHeader(entry: ZipEntry) {
        if (entry.name.length > MAX_ENTRY_NAME_CHARS || entry.name.indexOf('\u0000') >= 0) {
            throw IOException("ZIP entry name is invalid")
        }
        if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) {
            throw IOException("ZIP entry compression is unsupported")
        }
    }

    private fun drainIgnoredEntry(zip: ZipInputStream): Int {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        try {
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count > MAX_IGNORED_ENTRY_BYTES - total) {
                    throw IOException("Unrelated ZIP entry exceeds 1 MiB")
                }
                total += count
            }
            return total
        } finally {
            buffer.fill(0)
        }
    }
}

private fun safeDisplayName(entryName: String): String {
    val basename = entryName.substringAfterLast('/').substringAfterLast('\\').take(255)
    return basename.ifBlank { "keybox.xml" }
}

private fun readBoundedBytes(
    input: InputStream,
    maxBytes: Int,
    tooLargeMessage: String,
): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val accumulator = ZeroizingByteAccumulator(maxBytes)
    try {
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (!accumulator.append(buffer, count)) throw IOException(tooLargeMessage)
        }
        return accumulator.finish()
    } finally {
        buffer.fill(0)
        accumulator.clear()
    }
}

private class ZeroizingByteAccumulator(
    private val maxBytes: Int,
) {
    private var data = ByteArray(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
    private var size = 0

    fun append(
        source: ByteArray,
        count: Int,
    ): Boolean {
        if (count > maxBytes - size) return false
        ensureCapacity(size + count)
        source.copyInto(data, destinationOffset = size, startIndex = 0, endIndex = count)
        size += count
        return true
    }

    fun finish(): ByteArray {
        val result = data.copyOf(size)
        clear()
        return result
    }

    fun clear() {
        data.fill(0)
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= data.size) return
        var newSize = data.size.coerceAtLeast(1)
        while (newSize < required) {
            newSize = minOf(maxBytes, newSize * 2)
        }
        val replacement = ByteArray(newSize)
        data.copyInto(replacement, endIndex = size)
        data.fill(0)
        data = replacement
    }
}
