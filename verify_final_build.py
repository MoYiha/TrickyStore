import os

log_path = 'build_log.txt'
apk1 = 'encryptor-app/build/outputs/apk/release/encryptor-app-release.apk'
apk2 = 'service/build/outputs/apk/release/service-release.apk'

result = []

if os.path.exists(log_path):
    with open(log_path, 'r') as f:
        content = f.read()
        if "BUILD SUCCESSFUL" in content:
            result.append("BUILD_LOG: SUCCESS")
        else:
            result.append("BUILD_LOG: FAILURE")
            result.append("--- LAST 20 LINES ---")
            result.extend(content.splitlines()[-20:])
else:
    result.append("BUILD_LOG: MISSING")

if os.path.exists(apk1):
    result.append(f"APK_ENCRYPTOR: FOUND ({os.path.getsize(apk1)} bytes)")
else:
    result.append("APK_ENCRYPTOR: MISSING")

if os.path.exists(apk2):
    result.append(f"APK_SERVICE: FOUND ({os.path.getsize(apk2)} bytes)")
else:
    result.append("APK_SERVICE: MISSING")

with open('final_verification.txt', 'w') as f:
    f.write("\n".join(result))
