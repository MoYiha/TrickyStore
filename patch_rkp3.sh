cat << 'INNER_EOF' > /tmp/rkp_patch3.txt
<<<<<<< SEARCH
    private fun createCoseKeyMap(publicKey: java.security.PublicKey): java.util.Map<Any, Any> {
        val map = java.util.HashMap<Any, Any>()
=======
    private fun createCoseKeyMap(publicKey: java.security.PublicKey): Map<Any, Any> {
        val map = mutableMapOf<Any, Any>()
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

success = apply_patch('service/src/main/java/cleveres/tricky/cleverestech/RkpInterceptor.kt', '/tmp/rkp_patch3.txt')
if success:
    print('Patch applied successfully to RkpInterceptor.kt')
else:
    sys.exit(1)
"
