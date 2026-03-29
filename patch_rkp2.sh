cat << 'INNER_EOF' > /tmp/rkp_patch2.txt
<<<<<<< SEARCH
    private fun createCoseKeyMap(publicKey: java.security.PublicKey): java.util.Map<Object, Object> {
        val map = java.util.HashMap<Object, Object>()
=======
    private fun createCoseKeyMap(publicKey: java.security.PublicKey): java.util.Map<Any, Any> {
        val map = java.util.HashMap<Any, Any>()
>>>>>>> REPLACE
<<<<<<< SEARCH
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
=======
        try {
            val encoded = publicKey.encoded
            if (publicKey.algorithm == "Ed25519") {
                map.put(1, 1)   // kty: OKP
                map.put(3, -8)  // alg: EdDSA
                map.put(-1, 6)  // crv: Ed25519
                // Extract last 32 bytes for raw key
                val raw = ByteArray(32)
                System.arraycopy(encoded, encoded.size - 32, raw, 0, 32)
                map.put(-2, raw) // x coord
            } else if (publicKey.algorithm == "X25519") {
                map.put(1, 1)   // kty: OKP
                map.put(3, -25) // alg: ECDH-ES
                map.put(-1, 4)  // crv: X25519
                val raw = ByteArray(32)
                System.arraycopy(encoded, encoded.size - 32, raw, 0, 32)
                map.put(-2, raw) // x coord
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

success = apply_patch('service/src/main/java/cleveres/tricky/cleverestech/RkpInterceptor.kt', '/tmp/rkp_patch2.txt')
if success:
    print('Patch applied successfully to RkpInterceptor.kt')
else:
    sys.exit(1)
"
