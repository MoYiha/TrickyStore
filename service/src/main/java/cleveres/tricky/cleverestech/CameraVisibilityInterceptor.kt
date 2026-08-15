package cleveres.tricky.cleverestech

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

    private class CameraListenerProxy(
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
            if (passThrough || code !in CameraVisibilityInterceptor.cameraSpecificCallbackCodes) {
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
                    CameraVisibilityInterceptor.onStatusChangedTransaction,
                    CameraVisibilityInterceptor.onPhysicalCameraStatusChangedTransaction,
                    CameraVisibilityInterceptor.onTorchStatusChangedTransaction,
                    -> {
                        data.readInt()
                        data.readString()
                    }

                    CameraVisibilityInterceptor.onTorchStrengthLevelChangedTransaction,
                    CameraVisibilityInterceptor.onCameraOpenedTransaction,
                    CameraVisibilityInterceptor.onCameraOpenedInSharedModeTransaction,
                    CameraVisibilityInterceptor.onCameraClosedTransaction,
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
