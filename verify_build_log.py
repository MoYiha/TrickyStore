import sys

try:
    with open('build_log.txt', 'r') as f:
        content = f.read()
        if "BUILD SUCCESSFUL" in content:
            print("VERIFICATION_SUCCESS: Build was successful.")
        else:
            print("VERIFICATION_FAILURE: Build failed.")
            print("Last 20 lines:")
            print("\n".join(content.splitlines()[-20:]))
except Exception as e:
    print(f"Error reading log: {e}")
