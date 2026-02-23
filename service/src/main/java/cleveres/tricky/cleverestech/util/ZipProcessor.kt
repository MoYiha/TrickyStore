package cleveres.tricky.cleverestech.util

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import org.json.JSONObject
import cleveres.tricky.cleverestech.Logger

object ZipProcessor {
    data class ProcessedPack(
        val cboxFiles: List<Pair<String, ByteArray>>, // filename -> content
        val password: String?,
        val publicKey: String?,
        val config: JSONObject?
    )

    private const val MAX_ENTRY_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_TOTAL_SIZE = 10 * 1024 * 1024 // 10MB

    fun process(inputStream: InputStream): ProcessedPack? {
        val cboxFiles = ArrayList<Pair<String, ByteArray>>()
        var password: String? = null
        var publicKey: String? = null
        var config: JSONObject? = null
        var totalSize = 0

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        // Check for directory traversal
                        if (name.contains("..")) {
                            Logger.e("Zip entry contains directory traversal: $name")
                            return null
                        }

                        // Read content with limits
                        val content = readEntry(zis)
                        if (content == null) {
                            Logger.e("Zip entry too large: $name")
                            return null
                        }
                        totalSize += content.size
                        if (totalSize > MAX_TOTAL_SIZE) {
                            Logger.e("Total zip size exceeded limit")
                            return null
                        }

                        when {
                            name.endsWith(".cbox") -> {
                                cboxFiles.add(name to content)
                            }
                            name == "password.txt" -> {
                                password = String(content, StandardCharsets.UTF_8).trim()
                            }
                            name == "public_key.txt" -> {
                                publicKey = String(content, StandardCharsets.UTF_8).trim()
                            }
                            name == "config.json" -> {
                                try {
                                    config = JSONObject(String(content, StandardCharsets.UTF_8))
                                } catch (e: Exception) {
                                    Logger.e("Invalid config.json in zip")
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (cboxFiles.isEmpty()) {
                Logger.e("No .cbox files found in zip")
                return null
            }

            // Priority: config.json > individual files
            if (config != null) {
                if (config!!.has("password")) password = config!!.getString("password")
                if (config!!.has("public_key")) publicKey = config!!.getString("public_key")
            }

            return ProcessedPack(cboxFiles, password, publicKey, config)

        } catch (e: Exception) {
            Logger.e("Failed to process zip", e)
            return null
        }
    }

    private fun readEntry(zis: ZipInputStream): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val data = ByteArray(4096)
        var count = 0
        var total = 0
        while (zis.read(data).also { count = it } != -1) {
            total += count
            if (total > MAX_ENTRY_SIZE) return null
            buffer.write(data, 0, count)
        }
        return buffer.toByteArray()
    }
}
