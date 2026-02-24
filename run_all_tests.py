import subprocess
import os
import time
import signal
import sys

def run_command(command, shell=False):
    print(f"Running: {command}")
    try:
        subprocess.check_call(command, shell=shell)
        print("PASS")
        return True
    except subprocess.CalledProcessError as e:
        print(f"FAIL: {e}")
        return False

def start_server():
    print("Starting server...")
    # Kill any existing server on 8000
    subprocess.call(["pkill", "-f", "python3 -m http.server 8000"])

    # Extract HTML
    print("Extracting HTML...")
    subprocess.check_call(["python3", "extract_html.py"])

    # Start server
    server_process = subprocess.Popen(["python3", "-m", "http.server", "8000"], stdout=open("server.log", "w"), stderr=subprocess.STDOUT)
    time.sleep(3) # Wait for server to start
    return server_process

def main():
    results = {}

    # 1. Frontend Tests
    print("\n--- FRONTEND TESTS ---")
    server_process = start_server()
    try:
        results["verify_valid"] = run_command(["python3", "verify_valid.py"])
        results["verify_spoof_validation"] = run_command(["python3", "verify_spoof_validation.py"])
        results["verify_cbox"] = run_command(["python3", "verify_cbox.py"])
    finally:
        print("Stopping server...")
        server_process.kill()
        server_process.wait()
        subprocess.call(["pkill", "-f", "python3 -m http.server 8000"])

    # 2. Android Unit Tests
    print("\n--- ANDROID UNIT TESTS ---")
    results["unit_tests"] = run_command(["./gradlew", ":encryptor-app:testDebugUnitTest"])

    # 3. Build & Final Verification
    print("\n--- BUILD & VERIFICATION ---")
    # Redirect build output to build_log.txt for verify_final_build.py
    with open("build_log.txt", "w") as log:
        try:
            subprocess.check_call(["./gradlew", ":encryptor-app:assembleDebug"], stdout=log, stderr=subprocess.STDOUT)
            print("Build: PASS")
            results["build"] = True
        except subprocess.CalledProcessError:
            print("Build: FAIL")
            results["build"] = False

    results["verify_final_build"] = run_command(["python3", "verify_final_build.py"])

    # Read final verification result
    if os.path.exists("final_verification.txt"):
        with open("final_verification.txt", "r") as f:
            print("\nFinal Verification Report:")
            print(f.read())

    # Summary
    print("\n--- SUMMARY ---")
    all_passed = True
    for test, passed in results.items():
        status = "PASS" if passed else "FAIL"
        print(f"{test}: {status}")
        if not passed:
            all_passed = False

    if all_passed:
        print("\nALL TESTS PASSED")
        sys.exit(0)
    else:
        print("\nSOME TESTS FAILED")
        sys.exit(1)

if __name__ == "__main__":
    main()
