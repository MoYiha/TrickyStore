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
        val filename: String,
        val status: Status,
        val details: String
    )

    enum class Status {
        VALID, REVOKED, INVALID, ERROR
    }

    private const val CRL_URL = "https://android.googleapis.com/attestation/status"

    fun verify(keyboxDir: File): List<Result> {
        val results = ArrayList<Result>()
        val revokedSerials = fetchCrl()

        if (revokedSerials == null) {
            return listOf(Result("Global", Status.ERROR, "Failed to fetch CRL from Google"))
        }

        if (!keyboxDir.exists() || !keyboxDir.isDirectory) {
             return listOf(Result("Global", Status.ERROR, "Keybox directory not found"))
        }

        val files = keyboxDir.listFiles { _, name -> name.endsWith(".xml") } ?: emptyArray()

        for (file in files) {
            results.add(checkFile(file, revokedSerials))
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
            val json = JSONObject(jsonStr)
            val entries = json.optJSONArray("entries") ?: return emptySet()

            val set = HashSet<String>(entries.length())
            for (i in 0 until entries.length()) {
                set.add(entries.getString(i).lowercase())
            }
            set
        } catch (e: Exception) {
            Logger.e("Failed to fetch CRL", e)
            null
        }
    }

    private fun checkFile(file: File, revokedSerials: Set<String>): Result {
        return try {
            val keyboxes = file.reader().use { CertHack.parseKeyboxXml(it) }
            if (keyboxes.isEmpty()) {
                return Result(file.name, Status.INVALID, "No valid keyboxes found or parse error")
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
                            return Result(file.name, Status.REVOKED, "Certificate with SN $sn is revoked")
                        }
                    }
                }
            }

            Result(file.name, Status.VALID, "Active (${keyboxes.size} keys)")
        } catch (e: Exception) {
            Result(file.name, Status.ERROR, "Exception: ${e.message}")
        }
    }
}
