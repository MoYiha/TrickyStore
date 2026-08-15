from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one exact anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Config: explicit camera flag + bounded identity value. The value alone never
# activates runtime work; both the flag and value are required.
# ---------------------------------------------------------------------------
rel = "service/src/main/java/cleveres/tricky/cleverestech/Config.kt"
text = read(rel)
text = replace_once(
    text,
    '''        val serial: String? = null,\n        val visibleSimCount: Int? = null,\n    ) {''',
    '''        val serial: String? = null,\n        val visibleSimCount: Int? = null,\n        val visibleCameraCount: Int? = null,\n    ) {''',
    "IdentityOverrides camera count",
)
text = replace_once(
    text,
    '''    @Volatile\n    var isTelephonyEnabled = false\n\n    /**''',
    '''    @Volatile\n    var isTelephonyEnabled = false\n\n    @Volatile\n    var isCameraVisibilityEnabled = false\n        private set\n\n    /**''',
    "camera flag state",
)
text = replace_once(
    text,
    '''    fun getVisibleSimCount(uid: Int): Int? =\n        identityOverrides.visibleSimCount.takeIf { shouldApplyTelephonyPrivacy(uid) }\n\n    fun shouldApplyTelephonyPrivacy(uid: Int): Boolean {''',
    '''    fun getVisibleSimCount(uid: Int): Int? =\n        identityOverrides.visibleSimCount.takeIf { shouldApplyTelephonyPrivacy(uid) }\n\n    val shouldInterceptCameraVisibility: Boolean\n        get() = shouldRunCameraVisibility(isCameraVisibilityEnabled, identityOverrides.visibleCameraCount)\n\n    fun getVisibleCameraCount(uid: Int): Int? =\n        identityOverrides.visibleCameraCount.takeIf { isCameraVisibilityEnabled && isTargetedUid(uid) }\n\n    fun shouldApplyTelephonyPrivacy(uid: Int): Boolean {''',
    "camera runtime gate",
)
text = replace_once(
    text,
    '''    private fun updateTelephony(f: File?) {\n        val enabled = isRegularFlagFile(f)\n        val changed = isTelephonyEnabled != enabled\n        isTelephonyEnabled = enabled\n        PolicyState.onLegacySettingsChanged()\n        Logger.i("Telephony is ${if (isTelephonyEnabled) "enabled" else "disabled"}")\n        if (changed) signalRuntimeController()\n    }\n\n    private fun updateRkpPassthrough''',
    '''    private fun updateTelephony(f: File?) {\n        val enabled = isRegularFlagFile(f)\n        val changed = isTelephonyEnabled != enabled\n        isTelephonyEnabled = enabled\n        PolicyState.onLegacySettingsChanged()\n        Logger.i("Telephony is ${if (isTelephonyEnabled) "enabled" else "disabled"}")\n        if (changed) signalRuntimeController()\n    }\n\n    private fun updateCameraVisibility(f: File?) {\n        val enabled = isRegularFlagFile(f)\n        val changed = isCameraVisibilityEnabled != enabled\n        isCameraVisibilityEnabled = enabled\n        Logger.i("Camera visibility is ${if (enabled) "enabled" else "disabled"}")\n        if (changed) signalRuntimeController()\n    }\n\n    private fun updateRkpPassthrough''',
    "camera flag updater",
)
text = replace_once(
    text,
    '''            TELEPHONY_FILE -> updateTelephony(file)\n            RKP_PASSTHROUGH_FILE''',
    '''            TELEPHONY_FILE -> updateTelephony(file)\n            CAMERA_VISIBILITY_FILE -> updateCameraVisibility(file)\n            RKP_PASSTHROUGH_FILE''',
    "camera runtime refresh",
)
text = replace_once(
    text,
    '''                "ATTESTATION_ID_PHONE_NUMBER2",\n                "VISIBLE_SIM_COUNT",\n            )''',
    '''                "ATTESTATION_ID_PHONE_NUMBER2",\n                "VISIBLE_SIM_COUNT",\n                "VISIBLE_CAMERA_COUNT",\n            )''',
    "supported camera build var",
)
text = replace_once(
    text,
    '''        if (key == "VISIBLE_SIM_COUNT") return value.length == 1 && value[0] in '0'..'8'\n        when (key) {''',
    '''        if (key == "VISIBLE_SIM_COUNT") return value.length == 1 && value[0] in '0'..'8'\n        if (key == "VISIBLE_CAMERA_COUNT") return value.toIntOrNull()?.let { it in 0..16 } == true\n        when (key) {''',
    "camera count validation",
)
text = replace_once(
    text,
    '''            val previousVisibleSimCount = identityOverrides.visibleSimCount\n            val newIdentityOverrides =''',
    '''            val previousVisibleSimCount = identityOverrides.visibleSimCount\n            val previousVisibleCameraCount = identityOverrides.visibleCameraCount\n            val newIdentityOverrides =''',
    "previous camera count",
)
text = replace_once(
    text,
    '''                    serial = newVars["ATTESTATION_ID_SERIAL"],\n                    visibleSimCount = newVars["VISIBLE_SIM_COUNT"]?.toInt(),\n                )''',
    '''                    serial = newVars["ATTESTATION_ID_SERIAL"],\n                    visibleSimCount = newVars["VISIBLE_SIM_COUNT"]?.toInt(),\n                    visibleCameraCount = newVars["VISIBLE_CAMERA_COUNT"]?.toInt(),\n                )''',
    "parse camera count",
)
text = replace_once(
    text,
    '''            if (previousVisibleSimCount != newIdentityOverrides.visibleSimCount) signalRuntimeController()\n\n            CertHack.clearCertificateCache()''',
    '''            if (\n                previousVisibleSimCount != newIdentityOverrides.visibleSimCount ||\n                previousVisibleCameraCount != newIdentityOverrides.visibleCameraCount\n            ) {\n                signalRuntimeController()\n            }\n\n            CertHack.clearCertificateCache()''',
    "camera count lifecycle signal",
)
text = replace_once(
    text,
    '''    private const val TELEPHONY_FILE = "telephony"\n    private const val RKP_PASSTHROUGH_FILE''',
    '''    private const val TELEPHONY_FILE = "telephony"\n    private const val CAMERA_VISIBILITY_FILE = "camera_visibility"\n    private const val RKP_PASSTHROUGH_FILE''',
    "camera flag constant",
)
text = replace_once(
    text,
    '''        Logger.i("Applying profile: $profile")\n        when (profile) {''',
    '''        Logger.i("Applying profile: $profile")\n        // Hardware-visibility interceptors remain explicitly opt-in in every built-in profile.\n        removeConfigFiles(CAMERA_VISIBILITY_FILE)\n        when (profile) {''',
    "built-in profiles keep camera off",
)
text = replace_once(
    text,
    '''        updateTelephony(File(root, TELEPHONY_FILE))\n        updateRkpPassthrough''',
    '''        updateTelephony(File(root, TELEPHONY_FILE))\n        updateCameraVisibility(File(root, CAMERA_VISIBILITY_FILE))\n        updateRkpPassthrough''',
    "profile refresh camera flag",
)
text = replace_once(
    text,
    '''                    "ATTESTATION_ID_PHONE_NUMBER2" to "+1${RandomUtils.generateDigits(10)}",\n                )''',
    '''                    "ATTESTATION_ID_PHONE_NUMBER2" to "+1${RandomUtils.generateDigits(10)}",\n                    "VISIBLE_SIM_COUNT" to\n                        (RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1"),\n                    "VISIBLE_CAMERA_COUNT" to\n                        (RandomUtils.choose(listOf("1", "2", "2", "3", "3", "3", "4", "4", "4", "4")) ?: "2"),\n                )''',
    "boot random visibility values",
)
text = replace_once(
    text,
    '''                TELEPHONY_FILE -> updateTelephony(f)\n                RKP_PASSTHROUGH_FILE''',
    '''                TELEPHONY_FILE -> updateTelephony(f)\n                CAMERA_VISIBILITY_FILE -> updateCameraVisibility(f)\n                RKP_PASSTHROUGH_FILE''',
    "observer camera flag",
)
text = replace_once(
    text,
    '''        updateTelephony(File(root, TELEPHONY_FILE))\n        updateRkpPassthrough(File(root, RKP_PASSTHROUGH_FILE))''',
    '''        updateTelephony(File(root, TELEPHONY_FILE))\n        updateCameraVisibility(File(root, CAMERA_VISIBILITY_FILE))\n        updateRkpPassthrough(File(root, RKP_PASSTHROUGH_FILE))''',
    "initialize camera flag",
)
text = replace_once(
    text,
    '''        isTelephonyEnabled = false\n        isRkpPassthroughEnabled = false''',
    '''        isTelephonyEnabled = false\n        isCameraVisibilityEnabled = false\n        isRkpPassthroughEnabled = false''',
    "reset camera flag",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Pure camera lifecycle/count helpers.
# ---------------------------------------------------------------------------
write(
    "service/src/main/java/cleveres/tricky/cleverestech/CameraVisibility.kt",
    '''package cleveres.tricky.cleverestech\n\ninternal const val MAX_VISIBLE_CAMERA_COUNT = 16\n\ninternal fun shouldRunCameraVisibility(\n    enabled: Boolean,\n    configuredLimit: Int?,\n): Boolean = enabled && configuredLimit != null\n\ninternal fun boundedVisibleCameraCount(\n    realCount: Int,\n    configuredLimit: Int?,\n): Int {\n    val real = realCount.coerceAtLeast(0)\n    val limit = configuredLimit ?: return real\n    return minOf(real, limit.coerceIn(0, MAX_VISIBLE_CAMERA_COUNT))\n}\n''',
)


# ---------------------------------------------------------------------------
# Hidden API compile-only mirrors. Runtime classes come from the boot classpath.
# ---------------------------------------------------------------------------
write(
    "stub/src/main/java/android/hardware/ICameraService.java",
    '''package android.hardware;\n\nimport android.os.Binder;\nimport android.os.IBinder;\nimport android.os.IInterface;\n\n/** Minimal compile-only mirror. */\npublic interface ICameraService extends IInterface {\n    abstract class Stub extends Binder implements ICameraService {\n        public static ICameraService asInterface(IBinder obj) {\n            throw new UnsupportedOperationException();\n        }\n    }\n}\n''',
)
write(
    "stub/src/main/java/android/hardware/ICameraServiceListener.java",
    '''package android.hardware;\n\nimport android.os.Binder;\nimport android.os.IBinder;\nimport android.os.IInterface;\n\n/** Minimal compile-only mirror. */\npublic interface ICameraServiceListener extends IInterface {\n    abstract class Stub extends Binder implements ICameraServiceListener {\n        public static ICameraServiceListener asInterface(IBinder obj) {\n            throw new UnsupportedOperationException();\n        }\n    }\n}\n''',
)
write(
    "stub/src/main/java/android/hardware/CameraStatus.java",
    '''package android.hardware;\n\nimport android.os.Parcel;\nimport android.os.Parcelable;\n\n/** Minimal compile-only mirror. */\npublic class CameraStatus implements Parcelable {\n    public String cameraId;\n\n    @Override\n    public int describeContents() {\n        return 0;\n    }\n\n    @Override\n    public void writeToParcel(Parcel dest, int flags) {\n        throw new UnsupportedOperationException();\n    }\n\n    public static final Parcelable.Creator<CameraStatus> CREATOR =\n            new Parcelable.Creator<CameraStatus>() {\n                @Override\n                public CameraStatus createFromParcel(Parcel source) {\n                    throw new UnsupportedOperationException();\n                }\n\n                @Override\n                public CameraStatus[] newArray(int size) {\n                    return new CameraStatus[size];\n                }\n            };\n}\n''',
)
write(
    "stub/src/main/java/android/hardware/camera2/utils/ConcurrentCameraIdCombination.java",
    '''package android.hardware.camera2.utils;\n\nimport android.os.Parcel;\nimport android.os.Parcelable;\nimport java.util.Set;\n\n/** Minimal compile-only mirror whose erased Set return type is stable across API levels. */\npublic class ConcurrentCameraIdCombination implements Parcelable {\n    public Set<?> getConcurrentCameraIdCombination() {\n        throw new UnsupportedOperationException();\n    }\n\n    @Override\n    public int describeContents() {\n        return 0;\n    }\n\n    @Override\n    public void writeToParcel(Parcel dest, int flags) {\n        throw new UnsupportedOperationException();\n    }\n\n    public static final Parcelable.Creator<ConcurrentCameraIdCombination> CREATOR =\n            new Parcelable.Creator<ConcurrentCameraIdCombination>() {\n                @Override\n                public ConcurrentCameraIdCombination createFromParcel(Parcel source) {\n                    throw new UnsupportedOperationException();\n                }\n\n                @Override\n                public ConcurrentCameraIdCombination[] newArray(int size) {\n                    return new ConcurrentCameraIdCombination[size];\n                }\n            };\n}\n''',
)


# ---------------------------------------------------------------------------
# Camera Binder interceptor with bounded listener proxying. No process scan or
# injection occurs unless the explicit flag and count are both configured.
# ---------------------------------------------------------------------------
write(
    "service/src/main/java/cleveres/tricky/cleverestech/CameraVisibilityInterceptor.kt",
    r'''package cleveres.tricky.cleverestech

import android.hardware.CameraStatus
import android.hardware.ICameraService
import android.hardware.ICameraServiceListener
import android.hardware.camera2.utils.ConcurrentCameraIdCombination
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.ServiceManager
import android.os.SystemClock
import cleveres.tricky.cleverestech.binder.BinderInterceptor
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

object CameraVisibilityInterceptor : BinderInterceptor() {
    private const val CAMERA_SERVICE_DESCRIPTOR = "android.hardware.ICameraService"
    private const val CAMERA_LISTENER_DESCRIPTOR = "android.hardware.ICameraServiceListener"
    private const val CAMERA_SERVICE_NAME = "media.camera"
    private const val CAMERA_SERVER_PROCESS = "cameraserver"
    private const val MAX_LISTENER_PROXIES = 256
    private const val INJECTION_RETRY_INTERVAL_MS = 15_000L

    private val getNumberOfCamerasTransaction =
        getTransactCode(ICameraService.Stub::class.java, "getNumberOfCameras")
    private val addListenerTransaction =
        getTransactCode(ICameraService.Stub::class.java, "addListener")
    private val removeListenerTransaction =
        getTransactCode(ICameraService.Stub::class.java, "removeListener")
    private val getConcurrentCameraIdsTransaction =
        getTransactCode(ICameraService.Stub::class.java, "getConcurrentCameraIds")
    private val interceptedCodes =
        validTransactCodes(
            getNumberOfCamerasTransaction,
            addListenerTransaction,
            removeListenerTransaction,
            getConcurrentCameraIdsTransaction,
        )

    private val onStatusChangedTransaction =
        getTransactCode(ICameraServiceListener.Stub::class.java, "onStatusChanged")
    private val onPhysicalCameraStatusChangedTransaction =
        getTransactCode(ICameraServiceListener.Stub::class.java, "onPhysicalCameraStatusChanged")
    private val onTorchStatusChangedTransaction =
        getTransactCode(ICameraServiceListener.Stub::class.java, "onTorchStatusChanged")
    private val onTorchStrengthLevelChangedTransaction =
        getTransactCode(ICameraServiceListener.Stub::class.java, "onTorchStrengthLevelChanged")
    private val onCameraOpenedTransaction =
        getTransactCode(ICameraServiceListener.Stub::class.java, "onCameraOpened")
    private val onCameraOpenedInSharedModeTransaction =
        getTransactCode(ICameraServiceListener.Stub::class.java, "onCameraOpenedInSharedMode")
    private val onCameraClosedTransaction =
        getTransactCode(ICameraServiceListener.Stub::class.java, "onCameraClosed")
    private val cameraSpecificCallbackCodes =
        validTransactCodes(
            onStatusChangedTransaction,
            onPhysicalCameraStatusChangedTransaction,
            onTorchStatusChangedTransaction,
            onTorchStrengthLevelChangedTransaction,
            onCameraOpenedTransaction,
            onCameraOpenedInSharedModeTransaction,
            onCameraClosedTransaction,
        )

    private lateinit var cameraService: IBinder
    private var binderBackdoor: IBinder? = null
    private val triedCount = AtomicInteger(0)
    private val listenerLock = Any()
    private val listenerProxies = LinkedHashMap<IBinder, CameraListenerProxy>()

    @Volatile
    private var registered = false

    @Volatile
    private var deathRecipientLinked = false

    @Volatile
    private var injected = false

    @Volatile
    private var injectedPid: Int? = null

    @Volatile
    private var lastInjectionAttemptMs = 0L

    @Volatile
    private var cachedCameraServerPid: Int? = null

    private val cameraDeathRecipient =
        object : IBinder.DeathRecipient {
            override fun binderDied() {
                Logger.e("Camera service exited; resetting camera visibility state")
                synchronized(listenerLock) {
                    listenerProxies.values.forEach(CameraListenerProxy::dispose)
                    listenerProxies.clear()
                }
                registered = false
                deathRecipientLinked = false
                injected = false
                injectedPid = null
                binderBackdoor = null
                cachedCameraServerPid = null
                lastInjectionAttemptMs = 0L
                triedCount.set(0)
                Config.signalRuntimeController()
            }
        }

    override fun onPreTransact(
        target: IBinder,
        code: Int,
        flags: Int,
        callingUid: Int,
        callingPid: Int,
        data: Parcel,
    ): Result {
        if (!registered || target !== cameraService || code !in interceptedCodes) return Skip

        return when (code) {
            getNumberOfCamerasTransaction,
            getConcurrentCameraIdsTransaction,
            -> if (Config.getVisibleCameraCount(callingUid) != null) Continue else Skip

            addListenerTransaction -> {
                if (Config.getVisibleCameraCount(callingUid) == null) return Skip
                val original = readListenerBinder(data) ?: return Skip
                val proxy = getOrCreateProxy(original, callingUid) ?: return Skip
                rewriteListenerRequest(proxy)
            }

            removeListenerTransaction -> {
                val original = readListenerBinder(data) ?: return Skip
                val proxy = synchronized(listenerLock) { listenerProxies[original] } ?: return Skip
                rewriteListenerRequest(proxy)
            }

            else -> Skip
        }
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
        if (!registered || target !== cameraService || code !in interceptedCodes || resultCode != 0) {
            return Skip
        }

        return when (code) {
            getNumberOfCamerasTransaction -> rewriteCameraCount(callingUid, reply)
            addListenerTransaction -> rewriteListenerSnapshot(callingUid, data, reply)
            removeListenerTransaction -> finishListenerRemoval(data, reply)
            getConcurrentCameraIdsTransaction -> rewriteConcurrentCombinations(callingUid, reply)
            else -> Skip
        }
    }

    private fun rewriteCameraCount(
        callingUid: Int,
        reply: Parcel?,
    ): Result {
        reply ?: return Skip
        val limit = Config.getVisibleCameraCount(callingUid) ?: return Skip
        val position = reply.dataPosition()
        return try {
            reply.readException()
            val original = reply.readInt()
            val visible = boundedVisibleCameraCount(original, limit)
            if (visible == original) return Skip
            Parcel.obtain().also { replacement ->
                replacement.writeNoException()
                replacement.writeInt(visible)
            }.let { OverrideReply(0, it) }
        } catch (_: RuntimeException) {
            Skip
        } finally {
            reply.setDataPosition(position)
        }
    }

    private fun rewriteListenerSnapshot(
        callingUid: Int,
        request: Parcel,
        reply: Parcel?,
    ): Result {
        reply ?: return Skip
        val originalListener = readListenerBinder(request) ?: return Skip
        val proxy = synchronized(listenerLock) { listenerProxies[originalListener] } ?: return Skip
        val limit = Config.getVisibleCameraCount(callingUid)
        if (limit == null) {
            proxy.setPassThrough(true)
            return Skip
        }

        val position = reply.dataPosition()
        return try {
            reply.readException()
            val statuses = reply.createTypedArray(CameraStatus.CREATOR) ?: emptyArray()
            val visibleCount = boundedVisibleCameraCount(statuses.size, limit)
            val visibleIds = LinkedHashSet<String>(visibleCount)
            for (index in 0 until visibleCount) {
                statuses[index].cameraId?.let(visibleIds::add)
            }
            proxy.updateVisibleCameraIds(visibleIds)
            if (visibleCount == statuses.size) return Skip

            Parcel.obtain().also { replacement ->
                replacement.writeNoException()
                replacement.writeTypedArray(statuses.copyOf(visibleCount), 0)
            }.let { OverrideReply(0, it) }
        } catch (_: RuntimeException) {
            proxy.setPassThrough(true)
            Skip
        } finally {
            reply.setDataPosition(position)
        }
    }

    private fun finishListenerRemoval(
        request: Parcel,
        reply: Parcel?,
    ): Result {
        val original = readListenerBinder(request) ?: return Skip
        if (reply != null) {
            val position = reply.dataPosition()
            try {
                reply.readException()
            } catch (_: RuntimeException) {
                return Skip
            } finally {
                reply.setDataPosition(position)
            }
        }
        removeProxy(original)
        return Skip
    }

    private fun rewriteConcurrentCombinations(
        callingUid: Int,
        reply: Parcel?,
    ): Result {
        reply ?: return Skip
        val visibleIds = visibleCameraIdsForUid(callingUid) ?: return Skip
        val position = reply.dataPosition()
        return try {
            reply.readException()
            val combinations =
                reply.createTypedArray(ConcurrentCameraIdCombination.CREATOR) ?: return Skip
            val filtered =
                combinations.filter { combination ->
                    val ids = combinationCameraIds(combination)
                    ids == null || visibleIds.containsAll(ids)
                }
            if (filtered.size == combinations.size) return Skip
            Parcel.obtain().also { replacement ->
                replacement.writeNoException()
                replacement.writeTypedArray(filtered.toTypedArray(), 0)
            }.let { OverrideReply(0, it) }
        } catch (_: RuntimeException) {
            Skip
        } finally {
            reply.setDataPosition(position)
        }
    }

    private fun combinationCameraIds(combination: ConcurrentCameraIdCombination): Set<String>? {
        val raw =
            try {
                combination.getConcurrentCameraIdCombination()
            } catch (_: RuntimeException) {
                return null
            } catch (_: LinkageError) {
                return null
            }
        val result = LinkedHashSet<String>(raw.size)
        for (entry in raw) {
            val id =
                when (entry) {
                    is String -> entry
                    is android.util.Pair<*, *> -> entry.first as? String
                    else -> null
                } ?: return null
            result += id
        }
        return result
    }

    private fun readListenerBinder(data: Parcel): IBinder? {
        val position = data.dataPosition()
        return try {
            data.enforceInterface(CAMERA_SERVICE_DESCRIPTOR)
            val listener = data.readStrongBinder()
            listener.takeIf { data.dataAvail() == 0 }
        } catch (_: RuntimeException) {
            null
        } finally {
            data.setDataPosition(position)
        }
    }

    private fun rewriteListenerRequest(proxy: IBinder): Result =
        Parcel.obtain().also { replacement ->
            replacement.writeInterfaceToken(CAMERA_SERVICE_DESCRIPTOR)
            replacement.writeStrongBinder(proxy)
        }.let(::OverrideData)

    private fun getOrCreateProxy(
        original: IBinder,
        callingUid: Int,
    ): CameraListenerProxy? =
        synchronized(listenerLock) {
            listenerProxies[original]?.let { return@synchronized it }
            if (listenerProxies.size >= MAX_LISTENER_PROXIES) {
                Logger.w("Camera listener proxy limit reached; leaving additional listener unchanged")
                return@synchronized null
            }
            val proxy = CameraListenerProxy(original, callingUid)
            if (proxy.isDead()) return@synchronized null
            listenerProxies[original] = proxy
            proxy
        }

    private fun removeProxy(original: IBinder) {
        val proxy = synchronized(listenerLock) { listenerProxies.remove(original) } ?: return
        proxy.dispose()
    }

    private fun visibleCameraIdsForUid(uid: Int): Set<String>? =
        synchronized(listenerLock) {
            listenerProxies.values
                .firstOrNull { proxy -> proxy.ownerUid == uid && !proxy.isDead() }
                ?.visibleCameraIdsSnapshot()
        }

    private fun hasDeadProxies(): Boolean =
        synchronized(listenerLock) { listenerProxies.values.any(CameraListenerProxy::isDead) }

    private fun cleanupDeadProxies(): Boolean {
        val deadEntries =
            synchronized(listenerLock) {
                listenerProxies.entries
                    .filter { it.value.isDead() }
                    .map { it.key to it.value }
            }
        var clean = true
        deadEntries.forEach { (original, proxy) ->
            if (removeRemoteListener(proxy)) {
                synchronized(listenerLock) {
                    if (listenerProxies[original] === proxy) listenerProxies.remove(original)
                }
                proxy.dispose()
            } else {
                clean = false
            }
        }
        return clean
    }

    private fun removeAllRemoteListeners(): Boolean {
        val entries = synchronized(listenerLock) { listenerProxies.entries.map { it.key to it.value } }
        entries.forEach { (_, proxy) -> proxy.setPassThrough(true) }
        var clean = true
        entries.forEach { (original, proxy) ->
            if (removeRemoteListener(proxy)) {
                synchronized(listenerLock) {
                    if (listenerProxies[original] === proxy) listenerProxies.remove(original)
                }
                proxy.dispose()
            } else {
                clean = false
            }
        }
        return clean
    }

    private fun removeRemoteListener(proxy: IBinder): Boolean {
        if (removeListenerTransaction <= 0) return false
        if (!::cameraService.isInitialized || !cameraService.isBinderAlive) return true
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CAMERA_SERVICE_DESCRIPTOR)
            data.writeStrongBinder(proxy)
            if (!cameraService.transact(removeListenerTransaction, data, reply, 0)) return false
            reply.readException()
            true
        } catch (_: RemoteException) {
            false
        } catch (_: RuntimeException) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private inner class CameraListenerProxy(
        private val original: IBinder,
        val ownerUid: Int,
    ) : Binder() {
        @Volatile
        private var dead = false

        @Volatile
        private var passThrough = false

        @Volatile
        private var visibleCameraIds: Set<String> = emptySet()

        private val deathRecipient =
            object : IBinder.DeathRecipient {
                override fun binderDied() {
                    dead = true
                    Config.signalRuntimeController()
                }
            }

        init {
            try {
                original.linkToDeath(deathRecipient, 0)
            } catch (_: RemoteException) {
                dead = true
            }
        }

        fun isDead(): Boolean = dead

        fun setPassThrough(value: Boolean) {
            passThrough = value
        }

        fun updateVisibleCameraIds(ids: Set<String>) {
            visibleCameraIds = ids.toSet()
            passThrough = false
        }

        fun visibleCameraIdsSnapshot(): Set<String> = visibleCameraIds

        fun dispose() {
            try {
                original.unlinkToDeath(deathRecipient, 0)
            } catch (_: java.util.NoSuchElementException) {
            }
        }

        public override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int,
        ): Boolean {
            if (passThrough || code !in cameraSpecificCallbackCodes) {
                return forward(code, data, reply, flags)
            }
            val cameraId = readCameraIdFromCallback(code, data) ?: return forward(code, data, reply, flags)
            if (cameraId !in visibleCameraIds) return true
            return forward(code, data, reply, flags)
        }

        private fun readCameraIdFromCallback(
            code: Int,
            data: Parcel,
        ): String? {
            val position = data.dataPosition()
            return try {
                data.enforceInterface(CAMERA_LISTENER_DESCRIPTOR)
                when (code) {
                    onStatusChangedTransaction,
                    onPhysicalCameraStatusChangedTransaction,
                    onTorchStatusChangedTransaction,
                    -> {
                        data.readInt()
                        data.readString()
                    }

                    onTorchStrengthLevelChangedTransaction,
                    onCameraOpenedTransaction,
                    onCameraOpenedInSharedModeTransaction,
                    onCameraClosedTransaction,
                    -> data.readString()
                    else -> null
                }
            } catch (_: RuntimeException) {
                null
            } finally {
                data.setDataPosition(position)
            }
        }

        private fun forward(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int,
        ): Boolean {
            val position = data.dataPosition()
            return try {
                data.setDataPosition(0)
                original.transact(code, data, reply, flags)
            } catch (_: RemoteException) {
                dead = true
                Config.signalRuntimeController()
                false
            } finally {
                data.setDataPosition(position)
            }
        }
    }

    private fun findCameraServerPid(): Int? {
        val cachedPid = cachedCameraServerPid
        if (cachedPid != null && processMatches(cachedPid, CAMERA_SERVER_PROCESS)) return cachedPid
        cachedCameraServerPid = null

        val proc = File("/proc")
        if (!proc.exists() || !proc.isDirectory) return null
        try {
            java.nio.file.Files.newDirectoryStream(proc.toPath()).use { entries ->
                for (entry in entries) {
                    val pidString = entry.fileName.toString()
                    if (pidString.isEmpty() || pidString[0] !in '1'..'9') continue
                    val pid = pidString.toIntOrNull() ?: continue
                    if (processMatches(pid, CAMERA_SERVER_PROCESS)) {
                        cachedCameraServerPid = pid
                        return pid
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun processMatches(
        pid: Int,
        expectedBasename: String,
    ): Boolean {
        val buffer = ByteArray(1024)
        return try {
            java.nio.file.Files.newInputStream(File("/proc/$pid/cmdline").toPath()).use { stream ->
                val length = stream.read(buffer)
                if (length <= 0) return@use false
                var end = 0
                var start = 0
                while (end < length && buffer[end] != 0.toByte()) {
                    if (buffer[end] == '/'.code.toByte()) start = end + 1
                    end++
                }
                String(buffer, start, end - start) == expectedBasename
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getModuleDir(): File {
        val paths =
            listOf(
                "/data/adb/modules/cleverestricky",
                "/data/adb/ksu/modules/cleverestricky",
                "/data/adb/ap/modules/cleverestricky",
            )
        return paths.asSequence().map(::File).firstOrNull { it.isDirectory }
            ?: File("/data/adb/modules/cleverestricky")
    }

    private fun activateNativeHook(pid: Int): Boolean {
        return try {
            val modulePath = getModuleDir()
            val symbol = if (injected && injectedPid == pid) "resume" else "entry"
            val process =
                ProcessBuilder(
                    "$modulePath/inject",
                    pid.toString(),
                    "$modulePath/libcleverestricky.so",
                    symbol,
                ).redirectOutput(File("/dev/null"))
                    .redirectError(File("/dev/null"))
                    .start()
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Logger.e("Camera visibility injector timed out")
                false
            } else {
                val exitCode = process.exitValue()
                if (exitCode != 0) Logger.e("Camera visibility injector failed (exit=$exitCode)")
                exitCode == 0
            }
        } catch (error: Exception) {
            Logger.e("Camera visibility injector failed", error)
            false
        }
    }

    @Synchronized
    fun tryRun(): Boolean {
        if (!Config.shouldInterceptCameraVisibility) {
            return stop()
        }
        if (registered && ::cameraService.isInitialized && cameraService.isBinderAlive) {
            return cleanupDeadProxies()
        }
        registered = false

        val service = ServiceManager.getService(CAMERA_SERVICE_NAME) ?: return false
        val control = getBinderControlEndpoint(service)
        if (control == null) {
            val pid = findCameraServerPid() ?: return false
            val now = SystemClock.elapsedRealtime()
            if (lastInjectionAttemptMs != 0L && now - lastInjectionAttemptMs < INJECTION_RETRY_INTERVAL_MS) {
                return false
            }
            lastInjectionAttemptMs = now
            if (activateNativeHook(pid)) {
                injected = true
                injectedPid = pid
            }
            triedCount.incrementAndGet()
            return false
        }

        if (!Config.shouldInterceptCameraVisibility) {
            parkBinderHook(control)
            return true
        }

        cameraService = service
        binderBackdoor = control
        if (!registerBinderInterceptor(control, service, this, interceptedCodes)) {
            parkBinderHook(control)
            triedCount.incrementAndGet()
            return false
        }
        registered = true
        try {
            cameraService.linkToDeath(cameraDeathRecipient, 0)
            deathRecipientLinked = true
        } catch (_: RemoteException) {
            stop()
            return false
        }
        if (!Config.shouldInterceptCameraVisibility) {
            return stop()
        }
        triedCount.set(0)
        Logger.i("Camera visibility interceptor registered")
        return cleanupDeadProxies()
    }

    fun isRunning(): Boolean =
        registered &&
            ::cameraService.isInitialized &&
            cameraService.isBinderAlive &&
            !hasDeadProxies()

    @Synchronized
    fun stop(): Boolean {
        if (::cameraService.isInitialized && cameraService.isBinderAlive) {
            if (!removeAllRemoteListeners()) {
                Logger.d("Camera listener cleanup remains pending")
                return false
            }
        } else {
            synchronized(listenerLock) {
                listenerProxies.values.forEach(CameraListenerProxy::dispose)
                listenerProxies.clear()
            }
        }

        val targetAlive = ::cameraService.isInitialized && cameraService.isBinderAlive
        val control = binderBackdoor ?: if (targetAlive) getBinderControlEndpoint(cameraService) else null
        var stopped = control?.let(::clearAndParkBinderHook) == true
        if (!stopped && control != null) {
            if (registered) unregisterBinderInterceptor(control, cameraService, this)
            stopped = parkBinderHook(control)
        }
        if (!targetAlive || (!registered && control == null)) stopped = true
        if (!stopped) {
            binderBackdoor = control
            return false
        }

        if (deathRecipientLinked && ::cameraService.isInitialized) {
            try {
                cameraService.unlinkToDeath(cameraDeathRecipient, 0)
            } catch (_: java.util.NoSuchElementException) {
            }
            deathRecipientLinked = false
        }
        registered = false
        binderBackdoor = null
        return true
    }

    override fun onInterceptorReplaced() {
        registered = false
        binderBackdoor = null
        Config.signalRuntimeController()
    }
}
''',
)


# ---------------------------------------------------------------------------
# Main runtime controller: camera is fully independent and event-driven.
# ---------------------------------------------------------------------------
rel = "service/src/main/java/cleveres/tricky/cleverestech/Main.kt"
text = read(rel)
text = replace_once(
    text,
    '''        var previousTelephonyState: Boolean? = null\n        var previousDrmEngineState: Boolean? = null\n        var telephonyStopPending = false\n        var drmStopPending = false''',
    '''        var previousTelephonyState: Boolean? = null\n        var previousCameraState: Boolean? = null\n        var previousDrmEngineState: Boolean? = null\n        var telephonyStopPending = false\n        var cameraStopPending = false\n        var drmStopPending = false''',
    "Main camera state",
)
text = replace_once(
    text,
    '''            val drmEnabled = Config.shouldInterceptDrm\n            var drmSuccess = !drmEnabled || DrmInterceptor.isRunning()''',
    '''            val cameraEnabled = Config.shouldInterceptCameraVisibility\n            var cameraSuccess = !cameraEnabled || CameraVisibilityInterceptor.isRunning()\n            val drmEnabled = Config.shouldInterceptDrm\n            var drmSuccess = !drmEnabled || DrmInterceptor.isRunning()''',
    "Main camera health",
)
text = replace_once(
    text,
    '''            val drmJob =\n                if (drmEnabled && !drmSuccess) {''',
    '''            val cameraJob =\n                if (cameraEnabled && !cameraSuccess) {\n                    launch(Dispatchers.IO) {\n                        try {\n                            cameraSuccess = CameraVisibilityInterceptor.tryRun()\n                        } catch (e: Exception) {\n                            Logger.e("Camera visibility interceptor threw unexpected exception", e)\n                        }\n                    }\n                } else {\n                    null\n                }\n\n            val drmJob =\n                if (drmEnabled && !drmSuccess) {''',
    "Main camera job",
)
text = replace_once(
    text,
    '''            ksJob?.join()\n            telJob?.join()\n            drmJob?.join()''',
    '''            ksJob?.join()\n            telJob?.join()\n            cameraJob?.join()\n            drmJob?.join()''',
    "join camera job",
)
text = replace_once(
    text,
    '''            previousTelephonyState = if (telephonyStopPending) null else telephonyEnabled\n\n            if (!drmEnabled''',
    '''            previousTelephonyState = if (telephonyStopPending) null else telephonyEnabled\n\n            if (!cameraEnabled && (previousCameraState != false || cameraStopPending)) {\n                val wasPending = cameraStopPending\n                cameraStopPending = !CameraVisibilityInterceptor.stop()\n                if (cameraStopPending && !wasPending) {\n                    Logger.w("Camera visibility hook cleanup is incomplete; retry scheduled")\n                }\n                cameraSuccess = !cameraStopPending\n            } else if (cameraEnabled) {\n                cameraStopPending = false\n            }\n            previousCameraState = if (cameraStopPending) null else cameraEnabled\n\n            if (!drmEnabled''',
    "Main camera stop",
)
text = replace_once(
    text,
    '''            if (!telSuccess) Logger.d("Telephony interceptor not ready yet")\n            if (!drmSuccess) Logger.d("DRM privacy interceptor not ready yet")\n\n            val runtimeHealthy =\n                ksSuccess && telSuccess && drmSuccess && !telephonyStopPending && !drmStopPending''',
    '''            if (!telSuccess) Logger.d("Telephony interceptor not ready yet")\n            if (!cameraSuccess) Logger.d("Camera visibility interceptor not ready yet")\n            if (!drmSuccess) Logger.d("DRM privacy interceptor not ready yet")\n\n            val runtimeHealthy =\n                ksSuccess &&\n                    telSuccess &&\n                    cameraSuccess &&\n                    drmSuccess &&\n                    !telephonyStopPending &&\n                    !cameraStopPending &&\n                    !drmStopPending''',
    "Main camera health aggregate",
)
text = replace_once(
    text,
    '''                SubscriptionVisibilityInterceptor.stop()\n                DrmInterceptor.stopDrmInterceptor()''',
    '''                SubscriptionVisibilityInterceptor.stop()\n                CameraVisibilityInterceptor.stop()\n                DrmInterceptor.stopDrmInterceptor()''',
    "Main camera shutdown",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Web API and backup/toggle allowlists.
# ---------------------------------------------------------------------------
rel = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"
text = read(rel)
text = replace_once(
    text,
    '''            .put("serial", identity.serial ?: "")\n            .put("visible_sim_count", identity.visibleSimCount?.toString() ?: "")''',
    '''            .put("serial", identity.serial ?: "")\n            .put("visible_sim_count", identity.visibleSimCount?.toString() ?: "")\n            .put("visible_camera_count", identity.visibleCameraCount?.toString() ?: "")''',
    "identity camera JSON",
)
text = replace_once(
    text,
    '''            "visible_sim_count" -> RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1"\n            else ->''',
    '''            "visible_sim_count" -> RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1"\n            "visible_camera_count" ->\n                RandomUtils.choose(listOf("1", "2", "2", "3", "3", "3", "4", "4", "4", "4")) ?: "2"\n            else ->''',
    "random camera count",
)
text = replace_once(
    text,
    '''                    "serial",\n                    "visible_sim_count",\n                )''',
    '''                    "serial",\n                    "visible_sim_count",\n                    "visible_camera_count",\n                )''',
    "random all camera",
)
text = replace_once(
    text,
    '''            "device" -> putFields("serial")\n            "imei", "imei2", "imsi", "imsi2", "iccid", "iccid2", "meid", "meid2",\n            "phone_number", "phone_number2", "serial", "visible_sim_count" -> putFields(normalized)''',
    '''            "device" -> putFields("serial")\n            "hardware" -> putFields("visible_camera_count")\n            "imei", "imei2", "imsi", "imsi2", "iccid", "iccid2", "meid", "meid2",\n            "phone_number", "phone_number2", "serial", "visible_sim_count", "visible_camera_count" ->\n                putFields(normalized)''',
    "random camera scope",
)
text = replace_once(
    text,
    '''                "visible_sim_count" to "VISIBLE_SIM_COUNT",\n            )''',
    '''                "visible_sim_count" to "VISIBLE_SIM_COUNT",\n                "visible_camera_count" to "VISIBLE_CAMERA_COUNT",\n            )''',
    "camera identity field mapping",
)
text = replace_once(
    text,
    '''                "telephony",\n                "drm_passthrough",''',
    '''                "telephony",\n                "camera_visibility",\n                "drm_passthrough",''',
    "camera WebUI setting allowlist",
)
text = replace_once(
    text,
    '''                "telephony",\n                // Retained only for legacy backup compatibility.''',
    '''                "telephony",\n                "camera_visibility",\n                // Retained only for legacy backup compatibility.''',
    "camera backup allowlist",
)
text = replace_once(
    text,
    '''                            "ATTESTATION_ID_PHONE_NUMBER2" to "+1${RandomUtils.generateDigits(10)}",\n                        )''',
    '''                            "ATTESTATION_ID_PHONE_NUMBER2" to "+1${RandomUtils.generateDigits(10)}",\n                            "VISIBLE_SIM_COUNT" to\n                                (RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1"),\n                            "VISIBLE_CAMERA_COUNT" to\n                                (RandomUtils.choose(listOf("1", "2", "2", "3", "3", "3", "4", "4", "4", "4")) ?: "2"),\n                        )''',
    "environment reset visibility values",
)
write(rel, text)


# ---------------------------------------------------------------------------
# WebUI Identity form: camera count is a value; runtime activation remains a
# separate explicit switch in Identity Controls.
# ---------------------------------------------------------------------------
rel = "module/template/webroot/index.html"
text = read(rel)
text = replace_once(
    text,
    '''<div style="margin-top:10px;"><button type="button" onclick="runWithState(this, 'Generating...', () => generateRandomIdentity('telephony'))" style="width:100%;">Randomize Telephony</button></div>\n<div class="section-header">Device</div>''',
    '''<div style="margin-top:10px;"><button type="button" onclick="runWithState(this, 'Generating...', () => generateRandomIdentity('telephony'))" style="width:100%;">Randomize Telephony</button></div>\n<div class="section-header">Hardware visibility</div>\n<div class="grid-2">\n    <div><label for="inputVisibleCameraCount">Visible camera count</label><div class="identity-input-action"><div class="identity-input-slot"><input type="number" id="inputVisibleCameraCount" min="0" max="16" step="1" inputmode="numeric" aria-describedby="visibleCameraScope"></div><button type="button" class="identity-random-btn" title="Random" aria-label="Random" onclick="runWithState(this, 'Generating...', () => randomizeIdentityField('visible_camera_count'))">Random</button></div></div>\n    <div id="visibleCameraScope" class="scope-note" style="margin:0;align-self:end;">Limits discoverable camera IDs for selected apps. It never creates cameras that are not present.</div>\n</div>\n<div class="section-header">Device</div>''',
    "camera count UI",
)
text = replace_once(
    text,
    '''    inputVisibleSimCount: 'visible_sim_count'\n};''',
    '''    inputVisibleSimCount: 'visible_sim_count',\n    inputVisibleCameraCount: 'visible_camera_count'\n};''',
    "load camera field",
)
text = replace_once(
    text,
    '''visible_sim_count: 'inputVisibleSimCount'\n        });''',
    '''visible_sim_count: 'inputVisibleSimCount', visible_camera_count: 'inputVisibleCameraCount'\n        });''',
    "random camera map",
)
text = replace_once(
    text,
    '''    'inputIccid', 'inputIccid2', 'inputPhoneNumber', 'inputPhoneNumber2', 'inputSerial', 'inputVisibleSimCount'\n].forEach(id => {''',
    '''    'inputIccid', 'inputIccid2', 'inputPhoneNumber', 'inputPhoneNumber2', 'inputSerial',\n    'inputVisibleSimCount', 'inputVisibleCameraCount'\n].forEach(id => {''',
    "clear camera field",
)
text = replace_once(
    text,
    '''    visible_sim_count: 'inputVisibleSimCount'\n};''',
    '''    visible_sim_count: 'inputVisibleSimCount', visible_camera_count: 'inputVisibleCameraCount'\n};''',
    "save camera field",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Identity Controls: camera is independently opt-in, avoiding policy schema
# changes and avoiding surprise cameraserver activation from the master switch.
# ---------------------------------------------------------------------------
rel = "module/template/webroot/policy.js"
text = read(rel)
text = replace_once(
    text,
    '''function identityEnabled() {\n  if (!policyState || !policyState.features) return false;\n  return FEATURE_KEYS.some(([key]) => Boolean(policyState.features[key]));\n}''',
    '''function policyIdentityEnabled() {\n  if (!policyState || !policyState.features) return false;\n  return FEATURE_KEYS.some(([key]) => Boolean(policyState.features[key]));\n}\n\nfunction identityEnabled() {\n  return policyIdentityEnabled() || Boolean(legacyConfig && legacyConfig.camera_visibility);\n}''',
    "split policy and camera identity state",
)
text = replace_once(
    text,
    '''function identityControlsMarkup(prefix) {\n  const features = policyState ? policyState.features : {};\n  const identityOn = identityEnabled();\n  const children = FEATURE_KEYS.map(([key,title,desc]) => `<div class="row"><label for="${prefix}_${key}" style="flex:1;padding-right:10px"><strong>${escapeHtml(title)}</strong><span class="res-desc">${escapeHtml(desc)}</span></label>${switchMarkup(`${prefix}_${key}`,Boolean(features && features[key]),`data-policy-feature="${key}"`)}</div>`).join('');\n  return `<div class="ct-feature-card"><div class="row"><label for="${prefix}_master" style="flex:1;min-width:0;padding-right:12px"><strong>Identity</strong><span class="res-desc">Enable only the identity paths you need. Disabled paths do not start optional interceptors.</span></label>${switchMarkup(`${prefix}_master`,identityOn)}</div><div class="ct-subcontrols" id="${prefix}_children" ${identityOn ? '' : 'hidden'}>${children}</div>${helpMarkup('Identity is optional. Core Keystore/TEE protection is independent from this switch.')}</div>`;\n}''',
    '''function identityControlsMarkup(prefix) {\n  const features = policyState ? policyState.features : {};\n  const identityOn = policyIdentityEnabled();\n  const cameraOn = Boolean(legacyConfig && legacyConfig.camera_visibility);\n  const children = FEATURE_KEYS.map(([key,title,desc]) => `<div class="row"><label for="${prefix}_${key}" style="flex:1;padding-right:10px"><strong>${escapeHtml(title)}</strong><span class="res-desc">${escapeHtml(desc)}</span></label>${switchMarkup(`${prefix}_${key}`,Boolean(features && features[key]),`data-policy-feature="${key}"`)}</div>`).join('');\n  const core = `<div class="ct-feature-card"><div class="row"><label for="${prefix}_master" style="flex:1;min-width:0;padding-right:12px"><strong>Identity</strong><span class="res-desc">Enable only the identity paths you need. Disabled paths do not start optional interceptors.</span></label>${switchMarkup(`${prefix}_master`,identityOn)}</div><div class="ct-subcontrols" id="${prefix}_children" ${identityOn ? '' : 'hidden'}>${children}</div>${helpMarkup('Identity is optional. Core Keystore/TEE protection is independent from this switch.')}</div>`;\n  const camera = cardMarkup(`${prefix}_camera_visibility`,'Camera visibility','Filters camera discovery for selected apps. Disabled means no cameraserver interceptor is started.',cameraOn,helpMarkup('This only reduces discoverable real camera IDs; it does not create cameras or block direct access.'));\n  return `<div class="ct-feature-grid">${core}${camera}</div>`;\n}''',
    "camera identity control markup",
)
text = replace_once(
    text,
    '''  const children = panel.querySelector(`#${prefix}_children`);\n  if (master) master.onchange = () => {''',
    '''  const children = panel.querySelector(`#${prefix}_children`);\n  const cameraToggle = panel.querySelector(`#${prefix}_camera_visibility`);\n  if (cameraToggle) cameraToggle.onchange = () => setLegacyToggle('camera_visibility',cameraToggle.checked);\n  if (master) master.onchange = () => {''',
    "bind camera identity toggle",
)
text = replace_once(
    text,
    '''    await loadLegacyConfig();\n    renderFeatureCenter();\n    refreshPresentation();''',
    '''    await loadLegacyConfig();\n    renderFeatureCenter();\n    renderIdentityControls();\n    refreshPresentation();''',
    "legacy toggle rerender identity success",
)
text = replace_once(
    text,
    '''    await loadLegacyConfig();\n    renderFeatureCenter();\n    refreshPresentation();\n  }\n}''',
    '''    await loadLegacyConfig();\n    renderFeatureCenter();\n    renderIdentityControls();\n    refreshPresentation();\n  }\n}''',
    "legacy toggle rerender identity failure",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Localization for every built-in non-English locale.
# ---------------------------------------------------------------------------
rel = "module/template/webroot/ux.js"
text = read(rel)
translations = {
    "tr": ["Kamera görünürlüğü", "Seçili uygulamalar için kamera keşfini filtreler. Devre dışıyken cameraserver yakalayıcısı başlatılmaz.", "Bu yalnızca keşfedilebilir gerçek kamera kimliklerini azaltır; kamera oluşturmaz veya doğrudan erişimi engellemez.", "Donanım görünürlüğü", "Görünür kamera sayısı", "Seçili uygulamalar için keşfedilebilir kamera kimliklerini sınırlar. Var olmayan kamera oluşturmaz."],
    "zh-CN": ["相机可见性", "过滤所选应用的相机发现。关闭时不会启动 cameraserver 拦截器。", "它只减少可发现的真实相机 ID；不会创建相机，也不会阻止直接访问。", "硬件可见性", "可见相机数量", "限制所选应用可发现的相机 ID。不会创建实际不存在的相机。"],
    "es": ["Visibilidad de cámara", "Filtra el descubrimiento de cámaras para las apps seleccionadas. Desactivado no inicia el interceptor de cameraserver.", "Solo reduce IDs de cámaras reales detectables; no crea cámaras ni bloquea el acceso directo.", "Visibilidad de hardware", "Cantidad de cámaras visibles", "Limita los IDs de cámara detectables para las apps seleccionadas. Nunca crea cámaras inexistentes."],
    "de": ["Kamerasichtbarkeit", "Filtert die Kameraerkennung für ausgewählte Apps. Deaktiviert wird kein cameraserver-Interceptor gestartet.", "Dies reduziert nur erkennbare reale Kamera-IDs; es erzeugt keine Kameras und blockiert keinen direkten Zugriff.", "Hardwaresichtbarkeit", "Sichtbare Kameraanzahl", "Begrenzt erkennbare Kamera-IDs für ausgewählte Apps. Es werden keine nicht vorhandenen Kameras erzeugt."],
    "ru": ["Видимость камер", "Фильтрует обнаружение камер для выбранных приложений. В выключенном состоянии перехватчик cameraserver не запускается.", "Только уменьшает набор обнаруживаемых реальных ID камер; не создаёт камеры и не блокирует прямой доступ.", "Видимость оборудования", "Количество видимых камер", "Ограничивает обнаруживаемые ID камер для выбранных приложений. Не создаёт несуществующие камеры."],
    "id": ["Visibilitas kamera", "Memfilter penemuan kamera untuk aplikasi terpilih. Saat nonaktif, interceptor cameraserver tidak dimulai.", "Ini hanya mengurangi ID kamera nyata yang dapat ditemukan; tidak membuat kamera atau memblokir akses langsung.", "Visibilitas perangkat keras", "Jumlah kamera terlihat", "Membatasi ID kamera yang dapat ditemukan untuk aplikasi terpilih. Tidak membuat kamera yang sebenarnya tidak ada."],
    "hi": ["कैमरा दृश्यता", "चुने गए ऐप्स के लिए कैमरा खोज को फ़िल्टर करता है। बंद होने पर cameraserver इंटरसेप्टर शुरू नहीं होता।", "यह केवल खोजे जा सकने वाले वास्तविक कैमरा ID कम करता है; कैमरे बनाता या सीधी पहुँच रोकता नहीं है।", "हार्डवेयर दृश्यता", "दिखाई देने वाले कैमरों की संख्या", "चुने गए ऐप्स के लिए खोजे जा सकने वाले कैमरा ID सीमित करता है। मौजूद न होने वाले कैमरे नहीं बनाता।"],
    "ar": ["إظهار الكاميرا", "يرشح اكتشاف الكاميرات للتطبيقات المحددة. عند تعطيله لا يبدأ معترض cameraserver.", "يقلل فقط معرفات الكاميرات الحقيقية القابلة للاكتشاف؛ ولا ينشئ كاميرات أو يمنع الوصول المباشر.", "إظهار العتاد", "عدد الكاميرات الظاهرة", "يحد من معرفات الكاميرات القابلة للاكتشاف للتطبيقات المحددة، ولا ينشئ كاميرات غير موجودة."],
}
keys = [
    "Camera visibility",
    "Filters camera discovery for selected apps. Disabled means no cameraserver interceptor is started.",
    "This only reduces discoverable real camera IDs; it does not create cameras or block direct access.",
    "Hardware visibility",
    "Visible camera count",
    "Limits discoverable camera IDs for selected apps. It never creates cameras that are not present.",
]
for locale, values in translations.items():
    marker = "        'zh-CN': {" if locale == "zh-CN" else f"        {locale}: {{"
    start = text.find(marker)
    if start < 0:
        raise RuntimeError(f"locale marker missing: {locale}")
    anchor = "'Visible SIM count':"
    pos = text.find(anchor, start)
    if pos < 0:
        raise RuntimeError(f"visible SIM translation missing: {locale}")
    line_end = text.find("\n", pos)
    additions = ", ".join(
        f"{json.dumps(key, ensure_ascii=False)}: {json.dumps(value, ensure_ascii=False)}"
        for key, value in zip(keys, values)
    )
    text = text[: line_end + 1] + f"            {additions},\n" + text[line_end + 1 :]
write(rel, text)


# ---------------------------------------------------------------------------
# Rust injector allow-list remains exact; only cameraserver is added.
# ---------------------------------------------------------------------------
rel = "rust/native-core/src/injector_support.rs"
text = read(rel)
text = replace_once(
    text,
    '''    matches!(basename, b"keystore2" | b"com.android.phone")''',
    '''    matches!(\n        basename,\n        b"keystore2" | b"com.android.phone" | b"cameraserver"\n    )''',
    "Rust cameraserver allowlist",
)
text = replace_once(
    text,
    '''        assert!(is_supported_target_cmdline(b"com.android.phone\\0extra\\0"));\n        assert!(!is_supported_target_cmdline(''',
    '''        assert!(is_supported_target_cmdline(b"com.android.phone\\0extra\\0"));\n        assert!(is_supported_target_cmdline(b"/system/bin/cameraserver\\0"));\n        assert!(!is_supported_target_cmdline(b"cameraserver.helper\\0"));\n        assert!(!is_supported_target_cmdline(''',
    "Rust cameraserver allowlist test",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Tests: pure lifecycle/bounds, API validation/randomization, WebUI wiring.
# ---------------------------------------------------------------------------
write(
    "service/src/test/java/cleveres/tricky/cleverestech/CameraVisibilityTest.kt",
    '''package cleveres.tricky.cleverestech\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass CameraVisibilityTest {\n    @Test\n    fun `camera runtime requires both explicit flag and configured count`() {\n        assertFalse(shouldRunCameraVisibility(false, null))\n        assertFalse(shouldRunCameraVisibility(false, 2))\n        assertFalse(shouldRunCameraVisibility(true, null))\n        assertTrue(shouldRunCameraVisibility(true, 2))\n    }\n\n    @Test\n    fun `camera count only reduces real hardware`() {\n        assertEquals(4, boundedVisibleCameraCount(4, null))\n        assertEquals(2, boundedVisibleCameraCount(4, 2))\n        assertEquals(4, boundedVisibleCameraCount(4, 16))\n        assertEquals(0, boundedVisibleCameraCount(4, 0))\n        assertEquals(0, boundedVisibleCameraCount(-1, 2))\n    }\n}\n''',
)

rel = "service/src/test/java/cleveres/tricky/cleverestech/WebServerIdentityTest.kt"
text = read(rel)
text = replace_once(
    text,
    '''                .put("visible_sim_count", "1")''',
    '''                .put("visible_sim_count", "1")\n                .put("visible_camera_count", "3")''',
    "test save camera count",
)
text = replace_once(
    text,
    '''        assertEquals(1, Config.getIdentityOverrides().visibleSimCount)''',
    '''        assertEquals(1, Config.getIdentityOverrides().visibleSimCount)\n        assertEquals("3", saved.getString("visible_camera_count"))\n        assertEquals(3, Config.getIdentityOverrides().visibleCameraCount)''',
    "test persisted camera count",
)
text = replace_once(
    text,
    '''        assertEquals(400, postIdentity(JSONObject().put("visible_sim_count", "-1")).first)\n        assertEquals(400, postIdentity(JSONObject().put("unknown", "value")).first)''',
    '''        assertEquals(400, postIdentity(JSONObject().put("visible_sim_count", "-1")).first)\n        assertEquals(400, postIdentity(JSONObject().put("visible_camera_count", "17")).first)\n        assertEquals(400, postIdentity(JSONObject().put("visible_camera_count", "-1")).first)\n        assertEquals(400, postIdentity(JSONObject().put("unknown", "value")).first)''',
    "test invalid camera count",
)
text = replace_once(
    text,
    '''        assertTrue(json.getInt("visible_sim_count") in 0..2)''',
    '''        assertTrue(json.getInt("visible_sim_count") in 0..2)\n        assertTrue(json.getInt("visible_camera_count") in 1..4)''',
    "test random all camera",
)
text = replace_once(
    text,
    '''    @Test\n    fun `random template returns a bounded known template view`() {''',
    '''    @Test\n    fun `hardware random group contains only camera visibility`() {\n        val response = request("GET", "/api/random_identity?field=hardware")\n        assertEquals(200, response.first)\n        val json = JSONObject(response.second)\n        assertEquals(1, json.length())\n        assertTrue(json.getInt("visible_camera_count") in 1..4)\n    }\n\n    @Test\n    fun `random template returns a bounded known template view`() {''',
    "test hardware random group",
)
write(rel, text)

rel = "module/webui-tests/identity-randomization.test.js"
text = read(rel)
text += '''\nassert(index.includes('id="inputVisibleCameraCount"'), 'visible camera count control must exist');\nassert(index.includes("randomizeIdentityField('visible_camera_count')"), 'camera count must support single-field randomization');\nassert(index.includes("visible_camera_count: 'inputVisibleCameraCount'"), 'random payload must map visible camera count');\nassert(policy.includes("setLegacyToggle('camera_visibility'"), 'camera visibility must be an explicit opt-in legacy toggle');\nassert(policy.includes('Disabled means no cameraserver interceptor is started.'), 'camera control must document disabled lifecycle');\n'''
write(rel, text)
