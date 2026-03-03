import sys

with open('module/src/main/cpp/binder_interceptor.cpp', 'r') as f:
    content = f.read()

# I accidentally left `LOGI("Targeted property access: %s", name);`
content = content.replace("LOGI(\"Targeted property access (cache miss): %s\", name);\n        LOGI(\"Targeted property access: %s\", name);", "LOGI(\"Targeted property access (cache miss): %s\", name);")

with open('module/src/main/cpp/binder_interceptor.cpp', 'w') as f:
    f.write(content)
