cat << 'INNER_EOF' > /tmp/rkp_patch.txt
<<<<<<< SEARCH
    private fun createCertificateRequestResponse(
        keysToSign: Array<MacedPublicKey>?,
        challenge: ByteArray?,
        isV2: Boolean,
        deviceInfo: ByteArray
    ): ByteArray {
        return CertHack.createCertificateRequestResponse(
            keysToSign?.map { it.macedKey }?.filterNotNull() ?: emptyList(),
            challenge ?: ByteArray(0),
            deviceInfo
        ) ?: ByteArray(0)
    }

    private fun createDeviceInfo(uid: Int): ByteArray {
        val brand = Config.getBuildVar("BRAND", uid) ?: "google"
        val manufacturer = Config.getBuildVar("MANUFACTURER", uid) ?: "Google"
        val product = Config.getBuildVar("PRODUCT", uid) ?: "generic"
        val model = Config.getBuildVar("MODEL", uid) ?: "Pixel"
        val device = Config.getBuildVar("DEVICE", uid) ?: "generic"

        return CertHack.createDeviceInfoCbor(brand, manufacturer, product, model, device)
            ?: ByteArray(0)
    }

    private fun createProtectedData(): ByteArray {
        // COSE_Encrypt = [protected, unprotected, ciphertext, recipients]
        try {
            val protectedMap = java.util.HashMap<Int, Any>()
            protectedMap[1] = 3 // A256GCM
            val protHeader = cleveres.tricky.cleverestech.util.CborEncoder.encode(protectedMap)

            val coseEncrypt = java.util.ArrayList<Any>()
            coseEncrypt.add(protHeader)
            coseEncrypt.add(java.util.HashMap<Any, Any>()) // unprotected
            coseEncrypt.add(ByteArray(16)) // dummy ciphertext
            coseEncrypt.add(java.util.ArrayList<Any>()) // recipients

            return cleveres.tricky.cleverestech.util.CborEncoder.encode(coseEncrypt)
        } catch (e: Throwable) {
            Logger.e("failed to create protected data", e)
            return ByteArray(0)
        }
    }
}
=======
    private fun createCertificateRequestResponse(
        keysToSign: Array<MacedPublicKey>?,
        challenge: ByteArray?,
        isV2: Boolean,
        deviceInfo: ByteArray
    ): ByteArray {
        return CertHack.createCertificateRequestResponse(
            keysToSign?.map { it.macedKey }?.filterNotNull() ?: emptyList(),
            challenge ?: ByteArray(0),
            deviceInfo
        ) ?: ByteArray(0)
    }

    private fun createDeviceInfo(uid: Int): ByteArray {
        val brand = Config.getBuildVar("BRAND", uid) ?: "google"
        val manufacturer = Config.getBuildVar("MANUFACTURER", uid) ?: "Google"
        val product = Config.getBuildVar("PRODUCT", uid) ?: "generic"
        val model = Config.getBuildVar("MODEL", uid) ?: "Pixel"
        val device = Config.getBuildVar("DEVICE", uid) ?: "generic"

        return CertHack.createDeviceInfoCbor(brand, manufacturer, product, model, device)
            ?: ByteArray(0)
    }

    private fun createProtectedData(): ByteArray {
        try {
            // 1. Generate Ephemeral X25519 Key Pair
            val ephemeralKeyPair = cleveres.tricky.cleverestech.util.CryptoUtils.generateX25519KeyPair()

            // Dummy EEK Public Key (Google's prod EEK is usually passed or fetched)
            // For bypass purposes where we need a valid structure but don't have the real EEK,
            // we create a placeholder X25519 public key or parse it from args (if we passed it).
            // Here we just use a random key to simulate the ECDH process correctly.
            val dummyEekPair = cleveres.tricky.cleverestech.util.CryptoUtils.generateX25519KeyPair()
            val eekPublicKey = dummyEekPair.public.encoded

            // 2. ECDH Shared Secret
            val sharedSecret = cleveres.tricky.cleverestech.util.CryptoUtils.ecdhDeriveKey(ephemeralKeyPair.private, eekPublicKey)

            // 3. HKDF-SHA-256 to derive AES-256-GCM CEK (32 bytes)
            val salt = ByteArray(0)
            val info = "EekCek".toByteArray(Charsets.UTF_8)
            val cek = cleveres.tricky.cleverestech.util.CryptoUtils.hkdfSha256(sharedSecret, salt, info, 32)

            // 4. Create DICE Chain (Degenerate DICE)
            // Generate UDS (CDI_Leaf) Ed25519 key
            val udsKeyPair = cleveres.tricky.cleverestech.util.CryptoUtils.generateEd25519KeyPair()
            val udsPubCose = createCoseKeyMap(udsKeyPair.public)

            // Create a fake DICE chain payload: [UdsCerts, DiceCertChain, SignedData...]
            // For bypass, we build a simple array to represent the Open DICE chain
            val diceChain = java.util.ArrayList<Any>()
            diceChain.add(cleveres.tricky.cleverestech.util.CborEncoder.encode(udsPubCose))
            val diceChainBytes = cleveres.tricky.cleverestech.util.CborEncoder.encode(diceChain)

            // Payload = DICE Chain + MACed Keys (represented as simple array here)
            val payloadArray = java.util.ArrayList<Any>()
            payloadArray.add(diceChainBytes)
            val payloadBytes = cleveres.tricky.cleverestech.util.CborEncoder.encode(payloadArray)

            // 5. AES-GCM Encryption
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)

            // Protected Header for COSE_Encrypt
            val protectedMap = java.util.HashMap<Int, Any>()
            protectedMap[1] = 3 // A256GCM
            val protHeaderBytes = cleveres.tricky.cleverestech.util.CborEncoder.encode(protectedMap)

            // Encrypt_structure = ["Encrypt", protected, external_aad]
            val encStructure = java.util.ArrayList<Any>()
            encStructure.add("Encrypt")
            encStructure.add(protHeaderBytes)
            encStructure.add(ByteArray(0)) // external_aad
            val aadBytes = cleveres.tricky.cleverestech.util.CborEncoder.encode(encStructure)

            val ciphertext = cleveres.tricky.cleverestech.util.CryptoUtils.aesGcmEncrypt(cek, iv, aadBytes, payloadBytes)

            // 6. Build COSE_Encrypt0
            val unprotectedMap = java.util.HashMap<Any, Any>()
            unprotectedMap[5] = iv // IV

            val coseEncrypt = java.util.ArrayList<Any>()
            coseEncrypt.add(protHeaderBytes)
            coseEncrypt.add(unprotectedMap)
            coseEncrypt.add(ciphertext)

            // Include ephemeral public key in recipients or unprotected map based on spec.
            // Usually in recipients for COSE_Encrypt, but for v2 we follow the standard structure.
            val recipients = java.util.ArrayList<Any>()
            val recipientUnprotected = java.util.HashMap<Any, Any>()
            recipientUnprotected[-1] = createCoseKeyMap(ephemeralKeyPair.public) // ephemeral key

            val recipient = java.util.ArrayList<Any>()
            recipient.add(ByteArray(0)) // protected
            recipient.add(recipientUnprotected)
            recipient.add(ByteArray(0)) // ciphertext
            recipients.add(recipient)

            coseEncrypt.add(recipients)

            return cleveres.tricky.cleverestech.util.CborEncoder.encode(coseEncrypt)
        } catch (e: Throwable) {
            Logger.e("failed to create actual cryptographic protected data", e)
            return ByteArray(0)
        }
    }

    private fun createCoseKeyMap(publicKey: java.security.PublicKey): java.util.Map<Object, Object> {
        val map = java.util.HashMap<Object, Object>()
        try {
            val encoded = publicKey.encoded
            if (publicKey.algorithm == "Ed25519") {
                map.put(1 as Object, 1 as Object)   // kty: OKP
                map.put(3 as Object, -8 as Object)  // alg: EdDSA
                map.put(-1 as Object, 6 as Object)  // crv: Ed25519
                // Extract last 32 bytes for raw key
                val raw = ByteArray(32)
                System.arraycopy(encoded, encoded.size - 32, raw, 0, 32)
                map.put(-2 as Object, raw as Object) // x coord
            } else if (publicKey.algorithm == "X25519") {
                map.put(1 as Object, 1 as Object)   // kty: OKP
                map.put(3 as Object, -25 as Object) // alg: ECDH-ES
                map.put(-1 as Object, 4 as Object)  // crv: X25519
                val raw = ByteArray(32)
                System.arraycopy(encoded, encoded.size - 32, raw, 0, 32)
                map.put(-2 as Object, raw as Object) // x coord
            }
        } catch (e: Exception) {
            Logger.e("Failed to create COSE key map", e)
        }
        return map
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

success = apply_patch('service/src/main/java/cleveres/tricky/cleverestech/RkpInterceptor.kt', '/tmp/rkp_patch.txt')
if success:
    print('Patch applied successfully to RkpInterceptor.kt')
else:
    sys.exit(1)
"
