package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue

internal object RuntimeDiagnostics {
    private val workerExecutor = ThreadPoolExecutor(
        0, 2, 30L, TimeUnit.SECONDS, LinkedBlockingQueue(),
        { runnable -> Thread(runnable, "CleveresTricky-LogReader").apply { isDaemon = true } }
    ).apply { allowCoreThreadTimeOut(true) }
    private const val MAX_LOG_BYTES = 1024 * 1024
    private const val MAX_LOG_LINES = 2500
    private const val MAX_NATIVE_LOG_BYTES = 512L * 1024L
    private val cleveresMarkerRegex = Regex(" (CleveresTricky|cleverestricky|cleverestrickyd|cleverestricky_backend) *:")

    fun healthJson(root: File): JSONObject {
        val policy = PolicyState.stateJson()
        val features = policy.optJSONObject("features") ?: JSONObject()
        val rollbackAvailable = runCatching { IdentityRuntimeSnapshot.read(root) != null }.getOrDefault(false)
        return JSONObject()
            .put("policySource", policy.optString("source", "unknown"))
            .put("buildIdentity", features.optBoolean("buildIdentity", false))
            .put("regionIdentity", features.optBoolean("regionIdentity", false))
            .put("securityPatch", features.optBoolean("securityPatch", false))
            .put("cronAutoIdentity", CronAutoIdentity.statusJson())
            .put("identityCoordinator", IdentityCoordinator.diagnosticsJson())
            .put("profileAutoIdentity", ProfileAutoIdentityStore.diagnosticsJson())
            .put("rollbackAvailable", rollbackAvailable)
            .put("keyboxCount", CertHack.getKeyboxSourceCount())
            .put("keystoreInterceptor", KeystoreInterceptor.isRunning())
            .put("telephonyInterceptor", TelephonyInterceptor.isRunning())
    }

    fun cleveresLogs(root: File): String {
        val logcat = readProcessOutput(arrayOf("logcat", "-d", "-t", "2000"))
        val filtered =
            logcat
                .lineSequence()
                .filter { line -> cleveresMarkerRegex.containsMatchIn(line) }
                .takeLastBounded(MAX_LOG_LINES)
                .joinToString("\n")
        val native =
            runCatching { SafeConfigStore.readText(root, "native_runtime.log", MAX_NATIVE_LOG_BYTES) }
                .getOrNull()
                .orEmpty()
                .lineSequence()
                .takeLastBounded(1000)
                .joinToString("\n")
        val combined =
            buildString {
                if (filtered.isNotBlank()) append(filtered.trim())
                if (native.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("--- native runtime ---\n")
                    append(native.trim())
                }
            }
        return boundUtf8Tail(combined, MAX_LOG_BYTES)
    }

    private fun readProcessOutput(command: Array<String>): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val reader =
            FutureTask<String> {
                process.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    try {
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            if (count > MAX_LOG_BYTES - total) throw IOException("Log output exceeds bound")
                            output.write(buffer, 0, count)
                            total += count
                        }
                        String(output.toByteArray(), Charsets.UTF_8)
                    } finally {
                        buffer.fill(0)
                        output.reset()
                    }
                }
            }
        workerExecutor.execute(reader)
        return try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                throw IOException("logcat timed out")
            }
            reader.get(2, TimeUnit.SECONDS)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            if (!reader.isDone) reader.cancel(true)
        }
    }

    private fun boundUtf8Tail(
        text: String,
        maxBytes: Int,
    ): String {
        val lines = text.lineSequence().toList()
        val selected = ArrayDeque<String>()
        var totalBytes = 0
        for (index in lines.indices.reversed()) {
            val line = lines[index]
            val lineBytes = line.utf8ByteLength()
            val separatorBytes = if (selected.isEmpty()) 0 else 1
            if (lineBytes > maxBytes - totalBytes - separatorBytes) break
            selected.addFirst(line)
            totalBytes += lineBytes + separatorBytes
        }
        return selected.joinToString("\n")
    }

    private fun Sequence<String>.takeLastBounded(limit: Int): List<String> {
        val buffer = ArrayDeque<String>(limit)
        for (line in this) {
            if (buffer.size == limit) buffer.removeFirst()
            buffer.addLast(line)
        }
        return buffer.toList()
    }
}
