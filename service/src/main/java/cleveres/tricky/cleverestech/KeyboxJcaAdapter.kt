package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Unavoidable JVM/JCA adapter for key material already structurally validated and normalized by
 * the unprivileged Rust backend. Portable XML/PEM parsing intentionally does not live here.
 */
internal object KeyboxJcaAdapter {
    private val validationChallenge = "CleveresTricky keybox validation".toByteArray(Charsets.UTF_8)

    fun materialize(
        document: KeyboxWire.Document,
        filename: String,
    ): List<CertHack.KeyBox> =
        try {
            if (filename.isEmpty() || document.keys.isEmpty()) return emptyList()
            val parsed = ArrayList<CertHack.KeyBox>(document.keys.size)
            for (raw in document.keys) {
                val keybox = materializeKey(raw, filename) ?: return emptyList()
                parsed += keybox
            }
            parsed
        } finally {
            document.wipePrivateKeys()
        }

    private fun materializeKey(
        raw: KeyboxWire.RawKey,
        filename: String,
    ): CertHack.KeyBox? =
        try {
            val certificates = parseCertificates(raw.certificatesPem) ?: return null
            val leaf = certificates.firstOrNull() as? X509Certificate ?: return null
            val publicAlgorithm = normalizeAlgorithm(leaf.publicKey.algorithm) ?: return null
            val declaredAlgorithm = normalizeAlgorithm(raw.algorithm) ?: return null
            if (publicAlgorithm != declaredAlgorithm) return null

            val privateKey =
                KeyFactory
                    .getInstance(publicAlgorithm)
                    .generatePrivate(PKCS8EncodedKeySpec(raw.privateKeyPkcs8))
            if (normalizeAlgorithm(privateKey.algorithm) != publicAlgorithm) return null
            val keyPair = KeyPair(leaf.publicKey, privateKey)
            if (!provesPrivateKeyMatchesLeaf(keyPair, publicAlgorithm)) return null
            if (!validChain(certificates)) return null
            CertHack.KeyBox(keyPair, certificates, filename)
        } catch (_: Exception) {
            null
        }

    private fun parseCertificates(pemCertificates: List<String>): List<Certificate>? {
        if (pemCertificates.isEmpty()) return null
        val factory = CertificateFactory.getInstance("X.509")
        val certificates = ArrayList<Certificate>(pemCertificates.size)
        for (pem in pemCertificates) {
            if (pem.isEmpty()) return null
            val bytes = pem.toByteArray(Charsets.UTF_8)
            val certificate =
                try {
                    ByteArrayInputStream(bytes).use(factory::generateCertificate)
                } finally {
                    bytes.fill(0)
                }
            certificates += certificate
        }
        return certificates
    }

    private fun provesPrivateKeyMatchesLeaf(
        keyPair: KeyPair,
        algorithm: String,
    ): Boolean {
        val signature =
            Signature.getInstance(
                if (algorithm == "RSA") "SHA256withRSA" else "SHA256withECDSA",
            )
        signature.initSign(keyPair.private)
        signature.update(validationChallenge)
        val proof = signature.sign()
        return try {
            signature.initVerify(keyPair.public)
            signature.update(validationChallenge)
            signature.verify(proof)
        } finally {
            proof.fill(0)
        }
    }

    private fun validChain(certificates: List<Certificate>): Boolean {
        for (index in certificates.indices) {
            val certificate = certificates[index] as? X509Certificate ?: return false
            certificate.checkValidity()
            if (index + 1 < certificates.size) {
                certificate.verify(certificates[index + 1].publicKey)
            }
        }
        return true
    }

    private fun normalizeAlgorithm(algorithm: String?): String? =
        when {
            algorithm.equals("EC", ignoreCase = true) ||
                algorithm.equals("ECDSA", ignoreCase = true) -> "EC"
            algorithm.equals("RSA", ignoreCase = true) -> "RSA"
            else -> null
        }
}
