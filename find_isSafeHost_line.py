import sys

filename = 'service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt'
search_str = 'private fun isSafeHost(hostHeader: String?): Boolean {'

try:
    with open(filename, 'r') as f:
        lines = f.readlines()
        for i, line in enumerate(lines):
            if search_str in line:
                print(f"Found at line {i+1}")
                start = max(0, i - 10)
                end = min(len(lines), i + 5)
                for j in range(start, end):
                    print(f"{j+1}: {lines[j]}", end='')
                break
except Exception as e:
    print(f"Error: {e}")
