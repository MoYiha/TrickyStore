filepath = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"
with open(filepath, 'r') as f:
    content = f.read()

typo = "switchTab('info)"
fixed = "switchTab('info')"

if typo in content:
    content = content.replace(typo, fixed)
    print("Fixed typo: switchTab('info)")
else:
    print("Typo not found, maybe already fixed?")

with open(filepath, 'w') as f:
    f.write(content)
