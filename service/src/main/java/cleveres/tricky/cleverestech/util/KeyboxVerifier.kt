package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.keystore.CertHack
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import java.util.concurrent.Executors

object KeyboxVerifier {

    data class Result(
        val file: File,
        val filename: String,
        val status: Status,
        val details: String
    )

    enum class Status {
        VALID, REVOKED, INVALID, ERROR
    }

    private const val CRL_URL = "https://android.googleapis.com/attestation/status"

    fun verify(configDir: File, crlFetcher: () -> Set<String>? = { fetchCrl() }): List<Result> {
        val results = ArrayList<Result>()
        val revokedSerials = crlFetcher()

        if (revokedSerials == null) {
            return listOf(Result(File(""), "Global", Status.ERROR, "Failed to fetch CRL from Google"))
        }

        if (!configDir.exists() || !configDir.isDirectory) {
             return listOf(Result(File(""), "Global", Status.ERROR, "Config directory not found"))
        }

        // Check legacy keybox.xml
        val legacyFile = File(configDir, "keybox.xml")
        if (legacyFile.exists()) {
            results.add(checkFile(legacyFile, revokedSerials))
        }

        // Check jukebox files
        val keyboxDir = File(configDir, "keyboxes")
        if (keyboxDir.exists() && keyboxDir.isDirectory) {
            val files = keyboxDir.listFiles { _, name -> name.endsWith(".xml") } ?: emptyArray()
            for (file in files) {
                results.add(checkFile(file, revokedSerials))
            }
        }

        return results
    }

    private fun fetchCrl(): Set<String>? {
        return try {
            val url = URL(CRL_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cache-Control", "no-cache")

            if (conn.responseCode != 200) {
                Logger.e("CRL Fetch Failed: ${conn.responseCode}")
                return null
            }

            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            parseCrl(jsonStr)
        } catch (e: Exception) {
            Logger.e("Failed to fetch CRL", e)
            null
        }
    }

    fun parseCrl(jsonStr: String): Set<String> {
        val json = JSONObject(jsonStr)
        val entries = json.getJSONObject("entries")

        val set = HashSet<String>(entries.length())
        val keys = entries.keys()
        while (keys.hasNext()) {
            val decStr = keys.next()
            var added = false

            // Try treating as Hex (literal)
            if (decStr.matches(Regex("^[0-9a-fA-F]+$"))) {
                try {
                    val hexStr = java.math.BigInteger(decStr, 16).toString(16).lowercase()
                    set.add(hexStr)
                    added = true
                } catch (e: Exception) {
                    // Should not happen due to regex check, but safety first
                }
            }

            // Try treating as Decimal
            try {
                val hexStr = java.math.BigInteger(decStr).toString(16).lowercase()
                set.add(hexStr)
                added = true
            } catch (e: Exception) {
                // Not a valid decimal
            }

            if (!added) {
                Logger.e("Failed to parse CRL entry key: $decStr")
            }
        }
        return set
    }

    private fun checkFile(file: File, revokedSerials: Set<String>): Result {
        return try {
            val keyboxes = file.reader().use { CertHack.parseKeyboxXml(it) }
            if (keyboxes.isEmpty()) {
                return Result(file, file.name, Status.INVALID, "No valid keyboxes found or parse error")
            }

            for (kb in keyboxes) {
                val chain = kb.certificates()
                if (chain.isEmpty()) continue // Should not happen if parseKeyboxXml works

                // Check Leaf (EC) and Root (RSA) usually, but Google bans specific certificates.
                // The Python script checked indices 0 and 3.
                // But CertHack returns the full chain.
                // We should check ALL certificates in the chain just to be safe,
                // or at least the leaf and intermediate/root.

                for (cert in chain) {
                    if (cert is X509Certificate) {
                        val sn = cert.serialNumber.toString(16).lowercase()
                        if (revokedSerials.contains(sn)) {
                            return Result(file, file.name, Status.REVOKED, "Certificate with SN $sn is revoked")
                        }
                    }
                }
            }

            Result(file, file.name, Status.VALID, "Active (${keyboxes.size} keys)")
        } catch (e: Exception) {
            Result(file, file.name, Status.ERROR, "Error: ${e.javaClass.simpleName}")
        }
    }
}
