import re

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "r") as f:
    content = f.read()

# Replace ServiceSpecificException with standard exception handling or simple write
content = content.replace("import android.os.ServiceSpecificException", "")
content = content.replace("p.writeException(ServiceSpecificException(-29, \"TOO_MANY_OPERATIONS\"))", "p.writeInt(-29) // TOO_MANY_OPERATIONS")

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "w") as f:
    f.write(content)
