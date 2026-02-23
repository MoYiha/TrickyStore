import sys

try:
    with open('build_log.txt', 'r') as f:
        content = f.read()
        with open('verification_result.txt', 'w') as out:
            if "BUILD SUCCESSFUL" in content:
                out.write("VERIFICATION_SUCCESS: Build was successful.\n")
            else:
                out.write("VERIFICATION_FAILURE: Build failed.\n")
                out.write("Last 20 lines:\n")
                out.write("\n".join(content.splitlines()[-20:]))
except Exception as e:
    with open('verification_result.txt', 'w') as out:
        out.write(f"Error reading log: {e}")
