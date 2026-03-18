with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "r") as f:
    content = f.read()

content = content.replace("now - ops.first > 10000", "now - ops.first() > 10000")

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "w") as f:
    f.write(content)
