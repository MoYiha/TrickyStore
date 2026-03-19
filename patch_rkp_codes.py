import re

kt_path = "service/src/main/java/cleveres/tricky/cleverestech/RkpInterceptor.kt"
with open(kt_path, "r") as f:
    content = f.read()

patch = """        private val generateCertificateRequestV2Transaction =
            getTransactCode(IRemotelyProvisionedComponent.Stub::class.java, "generateCertificateRequestV2")

        val INTERCEPTED_CODES = intArrayOf(
            generateEcdsaP256KeyPairTransaction,
            generateCertificateRequestTransaction,
            generateCertificateRequestV2Transaction
        )"""

content = content.replace("""        private val generateCertificateRequestV2Transaction =
            getTransactCode(IRemotelyProvisionedComponent.Stub::class.java, "generateCertificateRequestV2")""", patch)

with open(kt_path, "w") as f:
    f.write(content)

print("done")
