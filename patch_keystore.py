import re

kt_path = "service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt"
with open(kt_path, "r") as f:
    content = f.read()

content = content.replace(
"""        keystore = b
        Logger.i("register for Keystore $keystore!")
        registerBinderInterceptor(bd, b, this)""",
"""        keystore = b
        Logger.i("register for Keystore $keystore!")
        val interceptedCodes = intArrayOf(getKeyEntryTransaction)
        registerBinderInterceptor(bd, b, this, interceptedCodes)""")

content = content.replace(
"""            val interceptor = SecurityLevelInterceptor(tee, SecurityLevel.TRUSTED_ENVIRONMENT)
            registerBinderInterceptor(bd, tee.asBinder(), interceptor)""",
"""            val interceptor = SecurityLevelInterceptor(tee, SecurityLevel.TRUSTED_ENVIRONMENT)
            registerBinderInterceptor(bd, tee.asBinder(), interceptor, SecurityLevelInterceptor.INTERCEPTED_CODES)""")

content = content.replace(
"""            val interceptor = SecurityLevelInterceptor(strongBox, SecurityLevel.STRONGBOX)
            registerBinderInterceptor(bd, strongBox.asBinder(), interceptor)""",
"""            val interceptor = SecurityLevelInterceptor(strongBox, SecurityLevel.STRONGBOX)
            registerBinderInterceptor(bd, strongBox.asBinder(), interceptor, SecurityLevelInterceptor.INTERCEPTED_CODES)""")

with open(kt_path, "w") as f:
    f.write(content)

print("done")
