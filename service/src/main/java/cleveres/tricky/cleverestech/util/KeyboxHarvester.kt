package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Config
import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.keystore.CertHack
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object KeyboxHarvester {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private const val DEFAULT_SOURCE = "https://raw.githubusercontent.com/TrickyStore/Public-Keyboxes/main/pool.xml"
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

    fun scheduleHarvest() {
        executor.scheduleAtFixedRate({
            harvest()
        }, 5, 360, TimeUnit.MINUTES)
    }

    private fun harvest() {
        val sourceUrl = Config.keyboxSourceUrl ?: DEFAULT_SOURCE
        Logger.i("Harvester: Starting harvest from $sourceUrl")

        try {
            val content = fetchUrl(sourceUrl) ?: return
            val keyboxes = ArrayList<CertHack.KeyBox>()

            if (content.trimStart().startsWith("<")) {
                keyboxes.addAll(CertHack.parseKeyboxXml(content.reader(), "harvested_direct.xml"))
            } else {
                content.lines().forEach { line ->
                    if (line.isNotBlank() && !line.startsWith("#")) {
                        val url = line.trim()
                        val xml = fetchUrl(url)
                        if (xml != null) {
                            keyboxes.addAll(CertHack.parseKeyboxXml(xml.reader(), "harvested_${url.hashCode()}.xml"))
                        }
                    }
                }
            }

            if (keyboxes.isEmpty()) {
                Logger.i("Harvester: No keyboxes found.")
                return
            }

            val crl = KeyboxVerifier.fetchCrl()
            if (crl == null) {
                Logger.e("Harvester: Failed to fetch CRL, aborting verification.")
                return
            }

            var added = 0
            for (kb in keyboxes) {
                if (KeyboxVerifier.verifyKeybox(kb, crl) == KeyboxVerifier.Status.VALID) {
                    saveKeybox(kb)
                    added++
                }
            }
            Logger.i("Harvester: Finished. Added/Updated $added valid keyboxes.")

        } catch (e: Exception) {
            Logger.e("Harvester: Error during harvest", e)
        }
    }

    private fun fetchUrl(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val length = conn.contentLength
                if (length > MAX_FILE_SIZE) {
                     Logger.e("Harvester: File too large ($length bytes)")
                     return null
                }

                val sb = StringBuilder()
                val buffer = CharArray(8192)
                var read: Int
                var total = 0
                conn.inputStream.bufferedReader().use { reader ->
                    while (reader.read(buffer).also { read = it } != -1) {
                        total += read
                        if (total > MAX_FILE_SIZE) {
                             Logger.e("Harvester: File exceeds size limit")
                             return null
                        }
                        sb.append(buffer, 0, read)
                    }
                }
                sb.toString()
            } else {
                Logger.e("Harvester: Failed to fetch $urlStr: ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Logger.e("Harvester: Exception fetching $urlStr", e)
            null
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val hexFormat = HexFormat { upperCase = false }

    @OptIn(ExperimentalStdlibApi::class)
    private fun saveKeybox(kb: CertHack.KeyBox) {
        try {
            val pubEncoded = kb.keyPair.public.encoded
            val hash = MessageDigest.getInstance("SHA-256").digest(pubEncoded)
            val hashStr = hash.toHexString(hexFormat).substring(0, 16)
            val fileName = "harvested_$hashStr.xml"
            val file = File(Config.keyboxDirectory, fileName)

            if (file.exists()) return

            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\"?>\n")
            sb.append("<AndroidAttestation>\n")
            sb.append("    <NumberOfKeyboxes>1</NumberOfKeyboxes>\n")
            sb.append("    <Keybox DeviceID=\"Harvested\">\n")

            val algo = kb.keyPair.public.algorithm
            val algoStr = if (algo == "EC" || algo == "ECDSA") "ecdsa" else "rsa"

            sb.append("        <Key algorithm=\"$algoStr\">\n")
            sb.append("            <PrivateKey format=\"pem\">\n")
            sb.append(getPem(kb.keyPair.private))
            sb.append("            </PrivateKey>\n")

            sb.append("            <CertificateChain>\n")
            sb.append("                <NumberOfCertificates>${kb.certificates.size}</NumberOfCertificates>\n")
            for (cert in kb.certificates) {
                sb.append("                <Certificate format=\"pem\">\n")
                sb.append(getPem(cert))
                sb.append("                </Certificate>\n")
            }
            sb.append("            </CertificateChain>\n")
            sb.append("        </Key>\n")
            sb.append("    </Keybox>\n")
            sb.append("</AndroidAttestation>\n")

            SecureFile.writeText(file, sb.toString())
            Logger.i("Harvester: Saved new keybox $fileName")
        } catch (e: Exception) {
            Logger.e("Harvester: Failed to save keybox", e)
        }
    }

    private fun getPem(key: PrivateKey): String {
        val encoded = Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(key.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----"
    }

    private fun getPem(cert: Certificate): String {
        val encoded = Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(cert.encoded)
        return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----"
    }
}
