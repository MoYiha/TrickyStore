cat << 'INNER_EOF' > /tmp/proxy_patch.txt
<<<<<<< SEARCH
object LocalRkpProxy {

    private const val RKP_MAC_KEY_ALGORITHM = "HmacSHA256"
    internal const val KEY_FILE_PATH = "/data/adb/cleverestricky/rkp_root_secret"

    // Dynamic Root Secret
    // Loaded from file or generated randomly to ensure persistence across reboots but
    // ability to rotate if caught.
    @Volatile
    private var serverHmacKey: ByteArray = ByteArray(32)

    init {
        loadOrGenerateKey()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun loadOrGenerateKey() {
=======
object LocalRkpProxy {

    private const val RKP_MAC_KEY_ALGORITHM = "HmacSHA256"
    internal const val KEY_FILE_PATH = "/data/adb/cleverestricky/rkp_root_secret"

    // Dynamic Root Secret
    // Loaded from file or generated randomly to ensure persistence across reboots but
    // ability to rotate if caught.
    @Volatile
    private var serverHmacKey: ByteArray = ByteArray(32)
    private val keyLock = Any()

    init {
        synchronized(keyLock) {
            loadOrGenerateKey()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun loadOrGenerateKey() {
>>>>>>> REPLACE
<<<<<<< SEARCH
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
=======
    /**
     * Checks if the key is expired and rotates it if possible.
     * Intended to be called by the maintenance service (root).
     */
    fun checkAndRotate() {
        synchronized(keyLock) {
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
    }

    /**
     * "Smart System": Rotates the root secret.
     * Use this if attestation starts failing. This invalidates all previous MACs,
     * effectively giving the device a new RKP identity relative to this proxy.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun rotateKey() {
        synchronized(keyLock) {
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
    }

    /**
     * "Provisions" the TEE (CertHack) with the HMAC key.
     */
    fun getMacKey(): ByteArray {
        synchronized(keyLock) {
            // Return a copy to prevent modification
            return serverHmacKey.clone()
        }
    }
>>>>>>> REPLACE
<<<<<<< SEARCH
    /**
     * Validates that a MACed public key has correct COSE_Mac0 structure.
     * Checks: 4-element CBOR array, protected header contains alg:5 (HMAC-256),
     * tag is exactly 32 bytes (HMAC-SHA256 output).
     */
    fun validateMacedPublicKey(macedKey: ByteArray): Boolean {
        if (macedKey.size < 10) {
            Logger.e("LocalRkpProxy: Validation Failed - MACed key too short (${macedKey.size} bytes)")
            return false
        }

        if (macedKey[0] != 0x84.toByte()) {
            Logger.e("LocalRkpProxy: Validation Failed - Not a valid COSE_Mac0 array (expected 0x84, got 0x${macedKey[0].toInt().and(0xFF).toString(16)})")
            return false
        }

        // Verify 32-byte HMAC tag at the end of the structure.
        // CBOR encodes 32-byte bstr as: 0x58 (bstr, 1-byte length) 0x20 (32).
        // tagLengthMarker could be negative for very small payloads, guarded by >= 1 check.
        val tagLengthMarker = macedKey.size - 33
        if (tagLengthMarker >= 1 && macedKey[tagLengthMarker] == 0x58.toByte()
            && macedKey[tagLengthMarker + 1] == 0x20.toByte()) {
            Logger.d("LocalRkpProxy: COSE_Mac0 structure validated (32-byte HMAC tag present)")
            return true
        }

        // If 32-byte tag not found at expected position, the structure is malformed
        Logger.e("LocalRkpProxy: Validation Failed - 32-byte HMAC tag not found at expected position (marker=$tagLengthMarker)")
        return false
    }
}
=======
    /**
     * Validates that a MACed public key has correct COSE_Mac0 structure.
     * Checks: 4-element CBOR array, extracts payload and tag, and cryptographically
     * verifies the HMAC-SHA256 signature using the root secret.
     */
    fun validateMacedPublicKey(macedKey: ByteArray): Boolean {
        try {
            val elements = cleveres.tricky.cleverestech.util.CryptoUtils.decodeSimpleCborArray(macedKey)
            if (elements.size != 4) {
                Logger.e("LocalRkpProxy: Validation Failed - Expected 4 elements, got ${elements.size}")
                return false
            }

            val protectedHeader = elements[0]
            val payload = elements[2]
            val tag = elements[3]

            // To be Maced: ["MAC0", protectedHeader, b"", payload]
            val macStructure = java.util.ArrayList<Any>()
            macStructure.add("MAC0")
            macStructure.add(protectedHeader)
            macStructure.add(ByteArray(0)) // external_aad
            macStructure.add(payload)

            val toBeMaced = CborEncoder.encode(macStructure)

            val currentKey = getMacKey()
            val hmac = Mac.getInstance(RKP_MAC_KEY_ALGORITHM)
            hmac.init(SecretKeySpec(currentKey, RKP_MAC_KEY_ALGORITHM))
            val expectedTag = hmac.doFinal(toBeMaced)

            // Note: CBOR byte string encoding adds a prefix (e.g., 0x58 0x20 for 32 bytes)
            // But skipCborObject in CryptoUtils returns the whole element including header.
            // Let's just decode it safely or do a constant-time comparison if possible.
            // Actually, `tag` contains the CBOR bstr header. We extract the last 32 bytes.
            if (tag.size < 32) return false
            val actualTagBytes = ByteArray(32)
            System.arraycopy(tag, tag.size - 32, actualTagBytes, 0, 32)

            val isValid = java.security.MessageDigest.isEqual(expectedTag, actualTagBytes)
            if (isValid) {
                Logger.d("LocalRkpProxy: COSE_Mac0 cryptographic signature validated successfully")
            } else {
                Logger.e("LocalRkpProxy: Validation Failed - Invalid MAC signature")
            }
            return isValid
        } catch (e: Exception) {
            Logger.e("LocalRkpProxy: Error validating MacedPublicKey", e)
            return false
        }
    }
}
>>>>>>> REPLACE
INNER_EOF
python3 -c "
import sys
import os

def apply_patch(file_path, patch_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        with open(patch_path, 'r', encoding='utf-8') as f:
            patch = f.read()

        blocks = patch.split('<<<<<<< SEARCH\n')[1:]
        for block in blocks:
            search, replace_and_rest = block.split('=======\n', 1)
            replace = replace_and_rest.split('>>>>>>> REPLACE', 1)[0]

            if search in content:
                content = content.replace(search, replace, 1)
            else:
                print(f'Warning: Could not find block in {file_path}')
                return False

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    except Exception as e:
        print(f'Error applying patch: {e}')
        return False

success = apply_patch('service/src/main/java/cleveres/tricky/cleverestech/rkp/LocalRkpProxy.kt', '/tmp/proxy_patch.txt')
if success:
    print('Patch applied successfully to LocalRkpProxy.kt')
else:
    sys.exit(1)
"
