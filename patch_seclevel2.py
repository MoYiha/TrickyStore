import re

kt_path = "service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt"
with open(kt_path, "r") as f:
    content = f.read()

patch = """                    val startNanos = System.nanoTime()
                    val pair = CertHack.generateKeyPair(callingUid, keyDescriptor, kgp, issuerKeyPair, issuerChain)
                        ?: return@runCatching
                    cleveres.tricky.cleverestech.util.TeeLatencySimulator.simulateGenerateKeyDelay(kgp.algorithm, System.nanoTime() - startNanos)"""

content = content.replace("""                    val pair = CertHack.generateKeyPair(callingUid, keyDescriptor, kgp, issuerKeyPair, issuerChain)
                        ?: return@runCatching""", patch)

with open(kt_path, "w") as f:
    f.write(content)

print("done")
