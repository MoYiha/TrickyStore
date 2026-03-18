with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "r") as f:
    content = f.read()

content = content.replace("val first: Long!", "val first: Long")
content = content.replace("ops.first()", "ops.firstOrNull() ?: 0L")

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "w") as f:
    f.write(content)
