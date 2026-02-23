import sys

filename = 'service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt'
search_str = 'class WebServer'

try:
    with open(filename, 'r') as f:
        lines = f.readlines()
        with open('class_info.txt', 'w') as out:
            found = False
            for i, line in enumerate(lines):
                if search_str in line:
                    out.write(f"Found at line {i+1}\n")
                    start = max(0, i - 10)
                    end = min(len(lines), i + 10)
                    for j in range(start, end):
                        out.write(f"{j+1}: {lines[j]}")
                    found = True
                    break
            if not found:
                out.write("Search string not found.\n")
except Exception as e:
    with open('class_info.txt', 'w') as out:
        out.write(f"Error: {e}\n")
