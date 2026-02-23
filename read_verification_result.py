import sys

try:
    with open('verification_result.txt', 'r') as f:
        content = f.read()
        print("---START_VERIFICATION_RESULT---")
        print(content)
        print("---END_VERIFICATION_RESULT---")
except Exception as e:
    print(f"Error reading verification result: {e}")
