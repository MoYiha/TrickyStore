package cleveres.tricky.cleverestech.rkp

import cleveres.tricky.cleverestech.Logger
import cleveres.tricky.cleverestech.util.CborEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Acts as a local "Back-end" / "Authority" for RKP requests.
 * Simulates the server component that:
 * 1. Holds the root secrets (HMAC Key).
 * 2. Validates the integrity/schema of requests.
 * 3. "Provisions" the device (provides the MAC key or signs data).
 */
object LocalRkpProxy {

    private const val RKP_MAC_KEY_ALGORITHM = "HmacSHA256"
    private const val KEY_FILE_PATH = "/data/adb/cleverestricky/rkp_root_secret"
    
    // Dynamic Root Secret
    // Loaded from file or generated randomly to ensure persistence across reboots but 
    // ability to rotate if caught.
    private var serverHmacKey: ByteArray = ByteArray(32)

    init {
        loadOrGenerateKey()
    }

    private fun loadOrGenerateKey() {
        try {
            val file = java.io.File(KEY_FILE_PATH)
            if (file.exists()) {
                serverHmacKey = file.readBytes()
                Logger.d("LocalRkpProxy: Loaded existing root secret")
            } else {
                rotateKey() // Generate new
            }
        } catch (e: Throwable) {
            Logger.e("LocalRkpProxy: Failed to load key, using random ephemeral", e)
            java.util.Random().nextBytes(serverHmacKey)
        }
    }

    /**
     * "Smart System": Rotates the root secret.
     * Use this if attestation starts failing. This invalidates all previous MACs,
     * effectively giving the device a new RKP identity relative to this proxy.
     */
    fun rotateKey() {
        Logger.d("LocalRkpProxy: Rotating Root Secret (Anti-Fingerprinting)")
        val newKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(newKey)
        serverHmacKey = newKey
        
        // Persist
        try {
            val file = java.io.File(KEY_FILE_PATH)
            file.parentFile?.mkdirs()
            file.writeBytes(serverHmacKey)
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
     * Simulation of server-side validation.
     * Validates that a MACed public key has the correct COSE structure.
     */
    fun validateMacedPublicKey(macedKey: ByteArray): Boolean {
        // Basic schema check: Should be a COSE_Mac0 array of 4 items.
        if (macedKey.isEmpty()) return false
        
        // Array of 4 items: Major Type 4 (0x80) | 4 = 0x84
        if (macedKey[0] != 0x84.toByte()) {
            Logger.e("LocalRkpProxy: Validation Failed - Not a valid COSE_Mac0 array (0x84)")
            return false
        }
        
        Logger.d("LocalRkpProxy: Schema validation passed for MacedPublicKey")
        return true
    }
}
