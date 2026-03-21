with open('.github/workflows/build.yml', 'r') as f:
    content = f.read()

# Fix the incorrect escaped characters from the previous python script issue
content = content.replace(r'MODULE_ZIP=\$(ls module/release/*-debug.zip | head -n 1)', 'MODULE_ZIP=$(ls module/release/*-debug.zip | head -n 1)')
content = content.replace(r'adb push "\$MODULE_ZIP"', 'adb push "$MODULE_ZIP"')

with open('.github/workflows/build.yml', 'w') as f:
    f.write(content)
