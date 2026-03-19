import re

kt_path = "service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt"
with open(kt_path, "r") as f:
    content = f.read()

content = content.replace(
"""            if (rkp != null) {
                Logger.i("register for RemotelyProvisionedComponent!")
                val interceptor = RkpInterceptor(rkp, SecurityLevel.TRUSTED_ENVIRONMENT)
                registerBinderInterceptor(bd, rkp.asBinder(), interceptor)""",
"""            if (rkp != null) {
                Logger.i("register for RemotelyProvisionedComponent!")
                val interceptor = RkpInterceptor(rkp, SecurityLevel.TRUSTED_ENVIRONMENT)
                registerBinderInterceptor(bd, rkp.asBinder(), interceptor, RkpInterceptor.INTERCEPTED_CODES)""")

with open(kt_path, "w") as f:
    f.write(content)

kt_path = "service/src/main/java/cleveres/tricky/cleverestech/RkpInterceptor.kt"
with open(kt_path, "r") as f:
    content = f.read()

patch = """    companion object {
        private val generateEcdsaP256KeyPairTransaction =
            getTransactCode(
                IRemotelyProvisionedComponent.Stub::class.java,
                "generateEcdsaP256KeyPair"
            ) // 3
        private val generateCertificateRequestTransaction =
            getTransactCode(
                IRemotelyProvisionedComponent.Stub::class.java,
                "generateCertificateRequest"
            ) // 4
        private val generateCertificateRequestV2Transaction =
            getTransactCode(
                IRemotelyProvisionedComponent.Stub::class.java,
                "generateCertificateRequestV2"
            ) // 5

        val INTERCEPTED_CODES = intArrayOf(
            generateEcdsaP256KeyPairTransaction,
            generateCertificateRequestTransaction,
            generateCertificateRequestV2Transaction
        )"""

content = content.replace(
"""    companion object {
        private val generateEcdsaP256KeyPairTransaction =
            getTransactCode(
                IRemotelyProvisionedComponent.Stub::class.java,
                "generateEcdsaP256KeyPair"
            ) // 3
        private val generateCertificateRequestTransaction =
            getTransactCode(
                IRemotelyProvisionedComponent.Stub::class.java,
                "generateCertificateRequest"
            ) // 4
        private val generateCertificateRequestV2Transaction =
            getTransactCode(
                IRemotelyProvisionedComponent.Stub::class.java,
                "generateCertificateRequestV2"
            ) // 5""", patch)

with open(kt_path, "w") as f:
    f.write(content)

print("done")
