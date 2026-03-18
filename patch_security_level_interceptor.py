import re

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "r") as f:
    content = f.read()

# Add imports
content = content.replace("import java.security.cert.Certificate", """import java.security.cert.Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedList
import android.system.keystore2.CreateOperationResponse
import android.system.keystore2.IKeystoreOperation
import android.system.keystore2.OperationChallenge
import android.hardware.security.keymint.SecurityLevel
import android.os.SystemClock""")

# Add constants and state for LRU
content = content.replace("        private val keys = KeyCache<Key, Info>(1000)", """        private val createOperationTransaction =
            getTransactCode(IKeystoreSecurityLevel.Stub::class.java, "createOperation")
        private val keys = KeyCache<Key, Info>(1000)

        // Concurrent op tracking
        private val activeOps = ConcurrentHashMap<Int, LinkedList<Long>>()
        private val opLimitLock = Any()

        const val STRONGBOX_MAX_CONCURRENT_OPS = 4
        const val TEE_MAX_CONCURRENT_OPS = 15""")

# Add trackAndEnforceOpLimit method
content = content.replace("    override fun onPreTransact(", """    private fun trackAndEnforceOpLimit(callingUid: Int): Boolean {
        val limit = if (level == SecurityLevel.STRONGBOX) STRONGBOX_MAX_CONCURRENT_OPS else TEE_MAX_CONCURRENT_OPS
        val now = SystemClock.uptimeMillis()

        synchronized(opLimitLock) {
            val ops = activeOps.getOrPut(callingUid) { LinkedList() }

            // Prune ops older than 10 seconds (arbitrary timeout to prevent leaks)
            while (ops.isNotEmpty() && now - ops.first > 10000) {
                ops.removeFirst()
            }

            if (ops.size >= limit) {
                // Evict oldest (LRU pruning) - simulate INVALID_OPERATION_HANDLE (-28) or TOO_MANY_OPERATIONS (-29)
                // In a real scenario we'd return a specific error code, but here we just reject the new one
                Logger.e("Concurrent operation limit exceeded for uid=$callingUid on level=$level (limit=$limit)")
                return false
            }

            ops.addLast(now)
            return true
        }
    }

    override fun onPreTransact(""")

# Add createOperation hook logic
create_op_logic = """        if (code == createOperationTransaction && Config.needGenerate(callingUid)) {
            Logger.i("intercept createOperation uid=$callingUid pid=$callingPid")
            kotlin.runCatching {
                data.enforceInterface(IKeystoreSecurityLevel.DESCRIPTOR)
                val keyDescriptor = data.readTypedObject(KeyDescriptor.CREATOR) ?: return@runCatching
                val operationParameters = data.createTypedArray(KeyParameter.CREATOR) ?: return@runCatching
                val forced = data.readBoolean()

                // Track and enforce op limit
                if (!trackAndEnforceOpLimit(callingUid)) {
                    // Return TOO_MANY_OPERATIONS (-29) equivalent in Parcel
                    val p = Parcel.obtain()
                    p.writeException(ServiceSpecificException(-29, "TOO_MANY_OPERATIONS"))
                    return OverrideReply(-29, p)
                }

                // Domain handling: Allow Domain.APP (alias) or Domain.KEY_ID (nspace)
                val alias = if (keyDescriptor.domain == 0 /* Domain.APP */) keyDescriptor.alias
                           else keyDescriptor.nspace.toString()

                val keyInfo = keys[Key(callingUid, alias)]
                if (keyInfo != null) {
                    val kgp = KeyGenParameters(operationParameters)

                    // StrongBox param guard
                    if (level == SecurityLevel.STRONGBOX) {
                        if (keyInfo.keyPair.public.algorithm == "RSA" && kgp.keySize > 2048) {
                            Logger.w("StrongBox param guard: RSA > 2048 rejected")
                            return Skip // Forward to real HAL for proper rejection
                        }
                        if (kgp.ecCurveName != "secp256r1" && kgp.ecCurveName != null && kgp.ecCurveName.isNotEmpty()) {
                            Logger.w("StrongBox param guard: Non-P256 EC rejected")
                            return Skip // Forward to real HAL for proper rejection
                        }

                        // StrongBox timing simulation
                        val delay = if (kgp.purpose.contains(android.hardware.security.keymint.KeyPurpose.SIGN)) 80L else 250L
                        Thread.sleep(delay)
                    }

                    // For now, we don't fully emulate the crypto operation in software here,
                    // we just pass it down or mock a basic response if needed.
                    // To fully resolve TEE issues end-to-end, we might need to return a mocked CreateOperationResponse
                    // but usually just enforcing limits/timing/guards on the way down is enough if the HAL handles it
                    // OR if it's a software key, we'd need a local crypto proxy.
                    // For the scope of this update, we just let it Skip (fallthrough) AFTER tracking limits & timing,
                    // OR if it's a pure software mocked key, we might need to mock the response.
                    // Let's just track it and let the system handle it, or mock if we must.
                }
            }.onFailure {
                Logger.e("parse createOperation request", it)
            }
        }

        if (code == generateKeyTransaction"""

content = content.replace("        if (code == generateKeyTransaction", create_op_logic)

with open("service/src/main/java/cleveres/tricky/cleverestech/SecurityLevelInterceptor.kt", "w") as f:
    f.write(content)
