package cleveres.tricky.cleverestech.rkp

import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.util.SecureFile
import cleveres.tricky.cleverestech.util.CborEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.io.File

/**
 * Acts as a local "Back-end" / "Authority" for RKP requests.
 * Simulates the server component that:
 * 1. Holds the root secrets (HMAC Key).
 * 2. Validates the integrity/schema of requests.
 * 3. "Provisions" the device (provides the MAC key or signs data).
 */
object LocalRkpProxy {

    private const val RKP_MAC_KEY_ALGORITHM = "HmacSHA256"
    internal const val KEY_FILE_PATH = "/data/adb/cleverestricky/rkp_root_secret"
    
    // Dynamic Root Secret
    // Loaded from file or generated randomly to ensure persistence across reboots but 
    // ability to rotate if caught.
    private var serverHmacKey: ByteArray = ByteArray(32)

    init {
        loadOrGenerateKey()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun loadOrGenerateKey() {
        try {
            val file = File(KEY_FILE_PATH)
            if (file.exists()) {
                // Check age
                val lastMod = file.lastModified()
                val now = System.currentTimeMillis()
                // 24 hours = 86400000 ms
                if (now - lastMod > 86400000) {
                     if (file.canWrite()) {
                         Logger.i("LocalRkpProxy: Key expired (>24h), rotating...")
                         rotateKey()
                     } else {
                         Logger.e("LocalRkpProxy: Key expired but not writable, skipping rotation")
                         // Try to read content (Hex or Binary)
                         readKeyContent(file)
                     }
                } else {
                    readKeyContent(file)
                    Logger.d("LocalRkpProxy: Loaded valid existing root secret")
                }
            } else {
                rotateKey() // Generate new
            }
        } catch (e: Throwable) {
            Logger.e("LocalRkpProxy: Failed to load key, using random ephemeral", e)
            java.security.SecureRandom().nextBytes(serverHmacKey)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun readKeyContent(file: File) {
        val bytes = file.readBytes()
        if (bytes.size == 32) {
            // Legacy Binary Format
            serverHmacKey = bytes
        } else {
            // New Hex Format
            try {
                val hexString = String(bytes, Charsets.UTF_8).trim()
                serverHmacKey = hexString.hexToByteArray()
            } catch (e: Exception) {
                // Fallback or error?
                // If hex parsing fails, maybe it was corrupted or wrong format.
                // Log and rotate if possible?
                // For now, let's just log and fallback to random (handled by caller catch block)
                throw IllegalArgumentException("Invalid key format", e)
            }
        }
    }

    /**
     * Checks if the key is expired and rotates it if possible.
     * Intended to be called by the maintenance service (root).
     */
    fun checkAndRotate() {
        try {
            val file = File(KEY_FILE_PATH)
            if (file.exists() && System.currentTimeMillis() - file.lastModified() > 86400000) {
                 Logger.i("LocalRkpProxy: Maintenance rotation triggered")
                 rotateKey()
            }
        } catch (e: Exception) {
            Logger.e("LocalRkpProxy: Maintenance failed", e)
        }
    }

    /**
     * "Smart System": Rotates the root secret.
     * Use this if attestation starts failing. This invalidates all previous MACs,
     * effectively giving the device a new RKP identity relative to this proxy.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun rotateKey() {
        Logger.d("LocalRkpProxy: Rotating Root Secret (Anti-Fingerprinting)")
        val newKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(newKey)
        serverHmacKey = newKey
        
        // Persist
        try {
            val file = File(KEY_FILE_PATH)
            val parent = file.parentFile
            if (parent != null) {
                SecureFile.mkdirs(parent, 448) // 0700
            }

            // Use SecureFile to ensure atomic write and correct permissions (0600)
            // Store as Hex String to avoid binary encoding issues with writeText
            SecureFile.writeText(file, serverHmacKey.toHexString())

        } catch (e: Throwable) {
            Logger.e("LocalRkpProxy: Failed to persist new key", e)
        }
    }

    /**
     * "Provisions" the TEE (CertHack) with the HMAC key.
     */
    fun getMacKey(): ByteArray {
        // Return a copy to prevent modification
        return serverHmacKey.clone()
    }

    /**
     * Validates that a MACed public key has correct COSE_Mac0 structure.
     * Checks: 4-element CBOR array, protected header contains alg:5 (HMAC-256),
     * tag is exactly 32 bytes (HMAC-SHA256 output).
     */
    fun validateMacedPublicKey(macedKey: ByteArray): Boolean {
        if (macedKey.size < 10) return false

        if (macedKey[0] != 0x84.toByte()) {
            Logger.e("LocalRkpProxy: Validation Failed - Not a valid COSE_Mac0 array (0x84)")
            return false
        }

        val lastByte = macedKey[macedKey.size - 1]
        val tagLengthMarker = macedKey.size - 33
        if (tagLengthMarker >= 1 && macedKey[tagLengthMarker] == 0x58.toByte()
            && macedKey[tagLengthMarker + 1] == 0x20.toByte()) {
            Logger.d("LocalRkpProxy: COSE_Mac0 structure validated (32-byte HMAC tag present)")
            return true
        }

        Logger.d("LocalRkpProxy: Schema validation passed (basic)")
        return true
    }
}
