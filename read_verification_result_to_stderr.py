import sys

try:
    with open('verification_result.txt', 'r') as f:
        content = f.read()
        print("---START_VERIFICATION_RESULT---", file=sys.stderr)
        print(content, file=sys.stderr)
        print("---END_VERIFICATION_RESULT---", file=sys.stderr)
except Exception as e:
    print(f"Error reading verification result: {e}", file=sys.stderr)
