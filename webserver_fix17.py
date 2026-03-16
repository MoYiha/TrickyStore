import sys
import re

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # To be extremely pedantic and avoid file descriptor leaks in case of an exception,
    # let's change `val stream = java.io.FileInputStream(cmdlineFile)` to `stream.use { ... }`

    # Or just use try-finally for closing the stream.

    # Let's replace:
    search_block = """                        val stream = java.io.FileInputStream(cmdlineFile)
                        val length = stream.read(buf)
                        stream.close()
                        if (length <= 0) return@runCatching
                        var end = 0
                        while (end < length && buf[end] != 0.toByte()) {
                            end++
                        }
                        val argv0 = String(buf, 0, end)"""

    replace_block = """                        val stream = java.io.FileInputStream(cmdlineFile)
                        val length = try {
                            stream.read(buf)
                        } finally {
                            stream.close()
                        }
                        if (length <= 0) return@runCatching
                        var end = 0
                        while (end < length && buf[end] != 0.toByte()) {
                            end++
                        }
                        val argv0 = String(buf, 0, end)"""

    search_block2 = """                    val stream = java.io.FileInputStream(cmdlineFile)
                    val length = stream.read(buf)
                    stream.close()
                    if (length <= 0) return@runCatching
                    var end = 0
                    while (end < length && buf[end] != 0.toByte()) {
                        end++
                    }
                    val argv0 = String(buf, 0, end)"""

    replace_block2 = """                    val stream = java.io.FileInputStream(cmdlineFile)
                    val length = try {
                        stream.read(buf)
                    } finally {
                        stream.close()
                    }
                    if (length <= 0) return@runCatching
                    var end = 0
                    while (end < length && buf[end] != 0.toByte()) {
                        end++
                    }
                    val argv0 = String(buf, 0, end)"""

    content = content.replace(search_block, replace_block)
    content = content.replace(search_block2, replace_block2)

    with open(filepath, 'w') as f:
        f.write(content)
    print(f"Patched {filepath}")

process_file('service/src/main/java/cleveres/tricky/cleverestech/DrmInterceptor.kt')
process_file('service/src/main/java/cleveres/tricky/cleverestech/TelephonyInterceptor.kt')
process_file('service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt')
