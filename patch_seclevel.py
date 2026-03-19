import re

kt_path = "service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt"
with open(kt_path, "r") as f:
    content = f.read()

patch = """        private val createOperationTransaction =
            getTransactCode(IKeystoreSecurityLevel.Stub::class.java, "createOperation")

        val INTERCEPTED_CODES = intArrayOf(generateKeyTransaction, createOperationTransaction)"""

content = content.replace("""        private val createOperationTransaction =
            getTransactCode(IKeystoreSecurityLevel.Stub::class.java, "createOperation")""", patch)

with open(kt_path, "w") as f:
    f.write(content)

print("done")
