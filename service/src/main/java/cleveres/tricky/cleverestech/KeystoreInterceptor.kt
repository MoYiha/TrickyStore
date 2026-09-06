package cleveres.tricky.cleverestech

import android.annotation.SuppressLint
import android.hardware.security.keymint.ErrorCode
import android.hardware.security.keymint.SecurityLevel
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.os.ServiceSpecificException
import android.os.SystemClock
import android.system.keystore2.IKeystoreService
import android.system.keystore2.KeyEntryResponse
import cleveres.tricky.cleverestech.binder.BinderInterceptor
import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.keystore.Utils
import kotlin.system.exitProcess

@SuppressLint("BlockedPrivateApi")
object KeystoreInterceptor : BinderInterceptor() {
    private const val FIRST_APPLICATION_UID = 10_000
    private const val MAX_PROC_SCAN_ENTRIES = 4_096
    private const val INJECTION_RETRY_INTERVAL_MS = 15_000L

    private val getSecurityLevelTransaction =
        getTransactCode(IKeystoreService.Stub::class.java, "getSecurityLevel") // 1

    private val getKeyEntryTransaction =
        getTransactCode(IKeystoreService.Stub::class.java, "getKeyEntry") // 2

    private lateinit var keystore: IBinder

    private var teeInterceptor: SecurityLevelInterceptor? = null
    private var teeTarget: IBinder? = null
    private var binderBackdoor: IBinder? = null

    @Volatile private var keystoreRegistered = false

    @Volatile private var registered = false

    @Volatile private var deathRecipientLinked = false

    override fun onPreTransact(
        target: IBinder,
        code: Int,
        flags: Int,
        callingUid: Int,
        callingPid: Int,
        data: Parcel,
    ): Result {
        if (target != keystore) return Skip

        // StrongBox discovery is fail-closed for targeted clients. Never hand a TEE child binder
        // back for a StrongBox request: that changes the requested hardware class and makes the
        // generated key/certificate contract internally inconsistent. Keystore2 documents
        // HARDWARE_TYPE_UNAVAILABLE as the correct result when a requested security level cannot
        // be provided.
        if (code == getSecurityLevelTransaction) {
            return if (
                Config.needHack(callingUid) &&
                requestedSecurityLevel(data) == SecurityLevel.STRONGBOX
            ) {
                Continue
            } else {
                Skip
            }
        }

        if (!CertHack.canHack()) return Skip
        if (code == getKeyEntryTransaction) {
            val targeted = Config.needHack(callingUid)
            val mayReadGrantedChain =
                callingUid >= FIRST_APPLICATION_UID && CertHack.hasCachedCertificateChains()
            return if (targeted || mayReadGrantedChain) Continue else Skip
        }
        return Skip
    }

    override fun onPostTransact(
        target: IBinder,
        code: Int,
        flags: Int,
        callingUid: Int,
        callingPid: Int,
        data: Parcel,
        reply: Parcel?,
        resultCode: Int,
    ): Result {
        if (target != keystore || reply == null || resultCode != 0) return Skip

        if (code == getSecurityLevelTransaction) {
            if (
                !Config.needHack(callingUid) ||
                requestedSecurityLevel(data) != SecurityLevel.STRONGBOX
            ) {
                return Skip
            }

            // Match the AIDL contract instead of returning the TEE binder under a StrongBox
            // request. This is intentionally independent of certificate/keybox availability:
            // callers either obtain genuine StrongBox from the platform or receive the documented
            // KeyMint unavailable error; a TEE key is never mislabeled as StrongBox.
            val p = Parcel.obtain()
            p.writeException(ServiceSpecificException(ErrorCode.HARDWARE_TYPE_UNAVAILABLE))
            return OverrideReply(0, p)
        }

        if (!CertHack.canHack() || code != getKeyEntryTransaction) return Skip

        try {
            reply.readException()
        } catch (e: Exception) {
            return Skip
        }
        val p = Parcel.obtain()
        try {
            val response = reply.readTypedObject(KeyEntryResponse.CREATOR)
            val metadata = response?.metadata
            if (metadata == null) {
                p.recycle()
                return Skip
            }

            // getKeyEntry exposes the platform-owned security level directly in KeyMetadata.
            // Generic replacement is a TEE-only policy, so reject StrongBox/software metadata
            // before cache hashing, X.509 parsing or Rust IPC. This keeps StrongBox reads fully
            // platform-owned and removes even the first-read provenance-classification overhead.
            if (metadata.keySecurityLevel != SecurityLevel.TRUSTED_ENVIRONMENT) {
                p.recycle()
                return Skip
            }

            // Caller-signed attestation leaves must keep their original issuer and signature.
            // This also avoids re-parsing ordinary non-attested keys on every getKeyEntry call.
            if (!Utils.isCertificateChainRewriteCandidate(metadata)) {
                p.recycle()
                return Skip
            }

            val targeted = Config.needHack(callingUid)
            val mayReadGrantedChain = callingUid >= FIRST_APPLICATION_UID

            // Duck Detector's timing probe creates the keys once and then measures repeated
            // service.getKeyEntry calls. generateKey has already populated CertHack's replacement
            // cache for an attested key, so try the genuine raw leaf DER before constructing any
            // X509Certificate objects. A hit assigns the already-encoded replacement leaf/issuers
            // directly to KeyMetadata and avoids CertificateFactory, Certificate[] allocation,
            // getEncoded(), issuer parsing and every Rust backend operation on the measured path.
            if (
                (targeted || mayReadGrantedChain) &&
                CertHack.applyCachedCertificateChain(metadata)
            ) {
                p.writeNoException()
                p.writeTypedObject(response, 0)
                return OverrideReply(0, p)
            }

            if (!targeted) {
                p.recycle()
                return Skip
            }

            // Cache miss is the exceptional/recovery path. Match the 2.5.8 ordering here: parse the
            // returned chain and let CertHack classify the uncached leaf after its own cache lookup.
            // CertHack rejects an ordinary non-attested leaf locally before any Rust IPC.
            val originalChain = Utils.getCertificateChain(response)
            val newChain =
                originalChain?.let {
                    CertHack.hackCertificateChain(it, callingUid).takeUnless { rewritten -> rewritten === it }
                }
            if (newChain != null) {
                Utils.putCertificateChain(response, newChain)
                p.writeNoException()
                p.writeTypedObject(response, 0)
                return OverrideReply(0, p)
            }
            p.recycle()
        } catch (t: Throwable) {
            Logger.e("Failed to rewrite a stored attestation certificate chain", t)
            p.recycle()
        }
        return Skip
    }

    private fun requestedSecurityLevel(data: Parcel): Int? {
        val position = data.dataPosition()
        return try {
            data.enforceInterface(IKeystoreService.DESCRIPTOR)
            if (data.dataAvail() < Integer.BYTES) null else data.readInt()
        } catch (_: RuntimeException) {
            null
        } finally {
            data.setDataPosition(position)
        }
    }

    private val triedCount = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile private var injected = false

    @Volatile private var cachedKeystorePid: Int? = null

    @Volatile private var injectedPid: Int? = null

    @Volatile private var lastInjectionAttemptMs = 0L

    private fun findKeystore2Pid(): Int? {
        val cachedPid = cachedKeystorePid
        if (cachedPid != null) {
            val buf = ByteArray(1024)
            try {
                val stream = java.io.FileInputStream("/proc/$cachedPid/cmdline")
                val length =
                    try {
                        stream.read(buf)
                    } finally {
                        stream.close()
                    }
                if (length > 0) {
                    var end = 0
                    var start = 0
                    while (end < length && buf[end] != 0.toByte()) {
                        if (buf[end] == 47.toByte()) start = end + 1 // Track last slash '/'
                        end++
                    }
                    val argv0 = String(buf, start, end - start)
                    if (argv0 == "keystore2") {
                        return cachedPid
                    }
                }
            } catch (e: Exception) {
                // Ignore file read errors
            }
            cachedKeystorePid = null
        }

        val proc = java.io.File("/proc")
        if (!proc.exists() || !proc.isDirectory) return null

        val buf = ByteArray(1024)
        try {
            java.nio.file.Files.newDirectoryStream(proc.toPath()).use { entries ->
                var scanned = 0
                for (entry in entries) {
                    if (++scanned > MAX_PROC_SCAN_ENTRIES) break
                    val pidStr = entry.fileName.toString()
                    if (pidStr.isEmpty() || pidStr[0] !in '1'..'9') continue
                    try {
                        val length =
                            java.nio.file.Files.newInputStream(entry.resolve("cmdline")).use { stream ->
                                stream.read(buf)
                            }
                        if (length <= 0) continue
                        var end = 0
                        var start = 0
                        while (end < length && buf[end] != 0.toByte()) {
                            if (buf[end] == 47.toByte()) start = end + 1
                            end++
                        }
                        if (String(buf, start, end - start) == "keystore2") {
                            val parsedPid = pidStr.toIntOrNull() ?: continue
                            cachedKeystorePid = parsedPid
                            return parsedPid
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun runNativeActivation(pid: Int, symbol: String): Boolean {
        val modulePath = getModuleDir()
        val injectPath = "$modulePath/inject"
        val process =
            try {
                ProcessBuilder(
                    injectPath,
                    pid.toString(),
                    "$modulePath/libcleverestricky.so",
                    symbol,
                    KernelIdentityManager.activationPayload(),
                ).redirectOutput(java.io.File("/dev/null"))
                    .redirectError(java.io.File("/dev/null"))
                    .start()
            } catch (error: Exception) {
                Logger.e("failed to start native activation", error)
                return false
            }
        return try {
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                Logger.e("native activation timed out after 30s, killing it")
                process.destroyForcibly()
                false
            } else {
                val exitCode = process.exitValue()
                if (exitCode != 0) Logger.e("native activation failed (exit=$exitCode)")
                exitCode == 0
            }
        } catch (error: Exception) {
            Logger.e("failed to run native activation", error)
            false
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }

    fun refreshKernelIdentity(): Boolean {
        val pid = findKeystore2Pid() ?: return false

        val needsActivation = synchronized(this) {
            injected && injectedPid == pid
        }
        if (!needsActivation) return true

        return runNativeActivation(pid, "resume")
    }

    fun tryRunKeystoreInterceptor(): Boolean {
        synchronized(this) {
            if (registered && ::keystore.isInitialized && keystore.isBinderAlive) return true
            registered = false
        }
        Logger.d("trying to register keystore interceptor (attempt=${triedCount.get()}) ...")
        val b =
            ServiceManager.getService("android.system.keystore2.IKeystoreService/default") ?: run {
                Logger.d("keystore2 service not yet available, will retry")
                return false
            }
        val bd = getBinderControlEndpoint(b)

        binderBackdoor = bd
        if (bd == null) {
            val pid = findKeystore2Pid()
            if (pid == null) {
                Logger.e("failed to find keystore2 pid! will retry (attempt=${triedCount.get()})")
                triedCount.incrementAndGet()
                return false
            }

            val now = SystemClock.elapsedRealtime()
            val symbol = synchronized(this) {
                if (lastInjectionAttemptMs != 0L && now - lastInjectionAttemptMs < INJECTION_RETRY_INTERVAL_MS) {
                    return false
                }
                lastInjectionAttemptMs = now
                if (injected && injectedPid == pid) "resume" else "entry"
            }

            Logger.i("trying to activate the keystore Binder hook ...")
            if (!runNativeActivation(pid, symbol)) {
                triedCount.incrementAndGet()
                return false
            }

            Logger.i("Keystore Binder hook activated successfully")
            synchronized(this) {
                injected = true
                injectedPid = pid
            }
            triedCount.incrementAndGet()
            return false
        }

        val ks = IKeystoreService.Stub.asInterface(b)
        val tee =
            try {
                ks.getSecurityLevel(SecurityLevel.TRUSTED_ENVIRONMENT)
            } catch (e: Exception) {
                null
            }

        // Root getSecurityLevel is intercepted only to fail targeted StrongBox discovery closed.
        // No StrongBox child binder is acquired or hooked, and a StrongBox request is never
        // redirected to the TEE child. getKeyEntry remains the cached TEE replacement/readback
        // path for targeted callers and granted-chain readers.
        val interceptedCodes =
            validTransactCodes(getSecurityLevelTransaction, getKeyEntryTransaction)

        val registeredHook = registerBinderInterceptor(bd, b, this, interceptedCodes)
        if (!registeredHook) {
            Logger.e("Failed to register the Keystore Binder interceptor")
            parkBinderHook(bd)
            return false
        }

        synchronized(this) {
            keystore = b
            binderBackdoor = bd
            keystoreRegistered = true
        }

        Logger.i("Keystore Binder interceptor registered")
        if (tee != null) {
            val interceptor = SecurityLevelInterceptor()
            if (!registerBinderInterceptor(
                    bd,
                    tee.asBinder(),
                    interceptor,
                    SecurityLevelInterceptor.INTERCEPTED_CODES,
                )
            ) {
                Logger.e("Failed to register the TEE SecurityLevel interceptor")
                stopKeystoreInterceptor()
                return false
            }
            synchronized(this) {
                teeInterceptor = interceptor
                teeTarget = tee.asBinder()
            }
            Logger.i("TEE SecurityLevel interceptor registered")
        } else {
            Logger.i("TEE SecurityLevel is unavailable")
        }

        var linkSuccess = false
        try {
            b.linkToDeath(Killer, 0)
            linkSuccess = true
        } catch (error: android.os.RemoteException) {
            Logger.w("Keystore exited before its interceptor lifecycle could be monitored")
        }

        synchronized(this) {
            if (linkSuccess) {
                deathRecipientLinked = true
            }
        }

        val linked = synchronized(this) { deathRecipientLinked }
        if (!linked) {
            stopKeystoreInterceptor()
            return false
        }

        synchronized(this) {
            registered = true
        }
        triedCount.set(0)
        return true
    }

    fun isRunning(): Boolean = registered && ::keystore.isInitialized && keystore.isBinderAlive

    fun stopKeystoreInterceptor(): Boolean {
        var targetAlive = false
        var control: IBinder? = null
        synchronized(this) {
            targetAlive = ::keystore.isInitialized && keystore.isBinderAlive
            control = binderBackdoor
        }

        if (control == null && targetAlive) {
            control = getBinderControlEndpoint(keystore)
        }

        var stopped = control?.let(::clearAndParkBinderHook) == true
        if (!stopped && control != null) {
            teeInterceptor?.let { interceptor ->
                teeTarget?.let { target ->
                    unregisterBinderInterceptor(control, target, interceptor)
                }
            }
            if (keystoreRegistered && ::keystore.isInitialized) {
                unregisterBinderInterceptor(control, keystore, this)
            }
            stopped = parkBinderHook(control)
        }

        val shouldUnlink = synchronized(this) {
            val hasKnownRegistration = registered || keystoreRegistered || teeInterceptor != null
            if (!targetAlive || (!hasKnownRegistration && control == null)) stopped = true
            if (!stopped) {
                binderBackdoor = control
                Logger.d("Keystore Binder hook cleanup remains pending")
                return false
            }
            deathRecipientLinked && ::keystore.isInitialized
        }

        if (shouldUnlink) {
            try {
                keystore.unlinkToDeath(Killer, 0)
            } catch (_: java.util.NoSuchElementException) {
                // The Binder driver already removed the recipient after death.
            }
        }

        synchronized(this) {
            deathRecipientLinked = false
            teeInterceptor = null
            teeTarget = null
            keystoreRegistered = false
            registered = false
            binderBackdoor = null
        }
        return true
    }

    override fun onInterceptorReplaced() {
        if (deathRecipientLinked && ::keystore.isInitialized) {
            try {
                keystore.unlinkToDeath(Killer, 0)
            } catch (_: java.util.NoSuchElementException) {
                // The Binder driver already removed the recipient after death.
            }
        }
        deathRecipientLinked = false
        registered = false
        keystoreRegistered = false
        binderBackdoor = null
        teeInterceptor = null
        teeTarget = null
        Config.signalRuntimeController()
    }

    object Killer : IBinder.DeathRecipient {
        override fun binderDied() {
            Logger.d("keystore exit, daemon restart")
            exitProcess(0)
        }
    }
}
