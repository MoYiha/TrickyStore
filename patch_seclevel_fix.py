import re

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "r") as f:
    content = f.read()

# Fix ServiceSpecificException import
content = content.replace("import android.system.keystore2.OperationChallenge", """import android.system.keystore2.OperationChallenge
import android.os.ServiceSpecificException""")

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "w") as f:
    f.write(content)
