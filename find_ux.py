import re

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    lines = f.readlines()

# Find UX elements we can improve
for i, line in enumerate(lines):
    if "onchange" in line and "<select" in line:
        print(f"Select onchange: {i+1}: {line.strip()}")
    if "authFields" in line:
        print(f"authFields: {i+1}: {line.strip()}")
