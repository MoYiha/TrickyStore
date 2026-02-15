package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.keystore.CertHack
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
    private val HASH_LENGTHS = listOf(32, 40, 64)

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

            // Use streaming parser to prevent OOM on large CRL files
            conn.inputStream.bufferedReader().use { reader ->
                parseCrl(reader)
            }
        } catch (e: Exception) {
            Logger.e("Failed to fetch CRL", e)
            null
        }
    }

    fun parseCrl(jsonStr: String): Set<String> {
        return parseCrl(java.io.StringReader(jsonStr))
    }

    fun parseCrl(reader: java.io.Reader): Set<String> {
        val set = HashSet<String>()
        val jsonReader = android.util.JsonReader(reader)
        var entriesFound = false
        try {
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val name = jsonReader.nextName()
                if (name == "entries") {
                    entriesFound = true
                    jsonReader.beginObject()
                    while (jsonReader.hasNext()) {
                        val decStr = jsonReader.nextName()
                        jsonReader.skipValue() // Value is "REVOKED"
                        processEntry(decStr, set)
                    }
                    jsonReader.endObject()
                } else {
                    jsonReader.skipValue()
                }
            }
            jsonReader.endObject()

            if (!entriesFound) {
                throw IOException("Invalid CRL: 'entries' object missing")
            }
        } catch (e: Exception) {
            Logger.e("Failed to parse CRL JSON", e)
            throw IOException("Failed to parse CRL", e)
        } finally {
            try { jsonReader.close() } catch (e: Exception) {}
        }

        if (!entriesFound) {
            throw IOException("CRL missing 'entries' field")
        }
        return set
    }

    private fun processEntry(decStr: String, set: HashSet<String>) {
        var added = false

        // Try treating as Decimal first (Spec compliant)
        var isDecimal = true
        if (decStr.length > 1 && decStr.startsWith("0")) {
            isDecimal = false
        } else {
            for (i in 0 until decStr.length) {
                if (!Character.isDigit(decStr[i])) {
                    isDecimal = false
                    break
                }
            }
        }

        if (isDecimal && decStr.isNotEmpty()) {
            try {
                val hexStr = java.math.BigInteger(decStr).toString(16).lowercase()
                set.add(hexStr)

                // BigInteger removes leading zeros. Hashes are fixed length (MD5=32, SHA1=40, SHA256=64).
                // If this decimal is actually a hash, we might need the padded version.
                for (targetLen in HASH_LENGTHS) {
                    if (hexStr.length < targetLen) {
                        set.add(hexStr.padStart(targetLen, '0'))
                    }
                }

                added = true
            } catch (e: Exception) {
                // Should not happen, but safe fallback
            }
        }

        // Ambiguity handling
        if (decStr.length == 32 || decStr.length == 40 || decStr.length == 64) {
            if (decStr.matches(Regex("^[0-9a-fA-F]+$"))) {
                set.add(decStr.lowercase())
            }
        }

        if (!added) {
            // Try treating as Hex (literal) as fallback
            if (decStr.matches(Regex("^[0-9a-fA-F]+$"))) {
                try {
                    val hexStr = java.math.BigInteger(decStr, 16).toString(16).lowercase()
                    set.add(hexStr)
                    added = true
                } catch (e: Exception) {
                }
            }
        }

        if (!added) {
            Logger.e("Failed to parse CRL entry key: $decStr")
        }
    }

    private fun checkFile(file: File, revokedSerials: Set<String>): Result {
        return try {
            val keyboxes = file.reader().use { CertHack.parseKeyboxXml(it) }
            if (keyboxes.isEmpty()) {
                return Result(file, file.name, Status.INVALID, "No valid keyboxes found or parse error")
            }

            for (kb in keyboxes) {
                val chain = kb.certificates()
                if (chain.isEmpty()) continue

                for (cert in chain) {
                    if (cert is X509Certificate) {
                        if (isRevoked(cert, revokedSerials)) {
                            val sn = cert.serialNumber.toString(16).lowercase()
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

    fun isRevoked(cert: X509Certificate, revokedSerials: Set<String>): Boolean {
        // 1. Serial Number (Hex)
        val sn = cert.serialNumber.toString(16).lowercase()
        if (revokedSerials.contains(sn)) return true

        // 2. Key ID Checks (Hash of Public Key)
        val publicKeyEncoded = cert.publicKey.encoded

        // SHA-1 (40 chars)
        if (checkHash(publicKeyEncoded, "SHA-1", revokedSerials)) return true

        // SHA-256 (64 chars)
        if (checkHash(publicKeyEncoded, "SHA-256", revokedSerials)) return true

        // MD5 (32 chars)
        if (checkHash(publicKeyEncoded, "MD5", revokedSerials)) return true

        return false
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val hexFormat = HexFormat { upperCase = false }

    @OptIn(ExperimentalStdlibApi::class)
    private fun checkHash(data: ByteArray, algorithm: String, set: Set<String>): Boolean {
        try {
            val digest = MessageDigest.getInstance(algorithm).digest(data)
            // Convert to Hex String (Zero Padded)
            val hex = digest.toHexString(hexFormat)
            return set.contains(hex)
        } catch (e: Exception) {
            return false
        }
    }
}
