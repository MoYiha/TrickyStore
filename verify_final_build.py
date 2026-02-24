import os

log_path = 'build_log.txt'
apk1_release = 'encryptor-app/build/outputs/apk/release/encryptor-app-release.apk'
apk1_debug = 'encryptor-app/build/outputs/apk/debug/encryptor-app-debug.apk'
apk2_release = 'service/build/outputs/apk/release/service-release.apk'
apk2_debug = 'service/build/outputs/apk/debug/service-debug.apk'

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

if os.path.exists(apk1_release):
    result.append(f"APK_ENCRYPTOR (Release): FOUND ({os.path.getsize(apk1_release)} bytes)")
elif os.path.exists(apk1_debug):
    result.append(f"APK_ENCRYPTOR (Debug): FOUND ({os.path.getsize(apk1_debug)} bytes)")
else:
    result.append("APK_ENCRYPTOR: MISSING")

if os.path.exists(apk2_release):
    result.append(f"APK_SERVICE (Release): FOUND ({os.path.getsize(apk2_release)} bytes)")
elif os.path.exists(apk2_debug):
    result.append(f"APK_SERVICE (Debug): FOUND ({os.path.getsize(apk2_debug)} bytes)")
else:
    result.append("APK_SERVICE: MISSING")

with open('final_verification.txt', 'w') as f:
    f.write("\n".join(result))
