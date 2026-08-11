package cleveres.tricky.cleverestech

import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.os.SystemClock
import cleveres.tricky.cleverestech.binder.BinderInterceptor
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Privacy-only hook for the modern stable-AIDL DRM HAL.
 *
 * The hook changes only IDrmPlugin.getPropertyByteArray("deviceUniqueId") for
 * applications explicitly configured with privacy=isolate. License exchange,
 * provisioning, content keys, session security level and every string property
 * remain on Android's genuine DRM path.
 */
object DrmInterceptor {
    private const val DRM_FACTORY_DESCRIPTOR = "android.hardware.drm.IDrmFactory"
    private const val DRM_PLUGIN_DESCRIPTOR = "android.hardware.drm.IDrmPlugin"
    private const val DRM_FACTORY_PREFIX = "$DRM_FACTORY_DESCRIPTOR/"
    private const val DEVICE_UNIQUE_ID = "deviceUniqueId"

    // android.hardware.drm is a frozen stable-AIDL interface. createDrmPlugin
    // is the first IDrmFactory method; getPropertyByteArray is the eleventh
    // IDrmPlugin method in the frozen API. Reflection is preferred when a Java
    // Stub is present, and these values are the wire-compatible fallback.
    private val createDrmPluginTransaction =
        resolveTransactionCode(
            "android.hardware.drm.IDrmFactory\$Stub",
            "createDrmPlugin",
            IBinder.FIRST_CALL_TRANSACTION,
        )
    private val getPropertyByteArrayTransaction =
        resolveTransactionCode(
            "android.hardware.drm.IDrmPlugin\$Stub",
            "getPropertyByteArray",
            IBinder.FIRST_CALL_TRANSACTION + 10,
        )

    private const val INJECTION_RETRY_INTERVAL_MS = 15_000L
    private const val RECONCILE_INTERVAL_MS = 30_000L
    private const val INJECT_TIMEOUT_SECONDS = 30L
    private const val MAX_FACTORY_SERVICES = 16
    private const val MAX_PLUGIN_BINDERS = 256

    private data class FactoryRegistration(
        val name: String,
        val binder: IBinder,
        val pid: Int,
        val control: IBinder,
        val interceptor: FactoryInterceptor,
        val deathRecipient: IBinder.DeathRecipient,
    )

    private data class PluginRegistration(
        val owner: String,
        val binder: IBinder,
        val control: IBinder,
    )

    private val factories = LinkedHashMap<String, FactoryRegistration>()
    private val plugins = LinkedHashMap<IBinder, PluginRegistration>()
    private val injectedPids = HashSet<Int>()
    private val lastInjectionAttempt = ConcurrentHashMap<Int, Long>()
    private val pluginInterceptor = PluginInterceptor()

    @Volatile
    private var lastReconcileMs = 0L

    @Volatile
    private var lastReconcileHealthy = false

    fun isRunning(): Boolean {
        if (!lastReconcileHealthy) return false
        val age = SystemClock.elapsedRealtime() - lastReconcileMs
        if (age !in 0 until RECONCILE_INTERVAL_MS) return false
        return synchronized(this) { factories.values.all { it.binder.isBinderAlive } }
    }

    /**
     * Reconciles all currently declared/running stable-AIDL DRM factories.
     * A missing factory is not an error: devices may expose only a legacy HIDL
     * DRM implementation. The controller periodically rescans for lazy HALs.
     */
    @Synchronized
    fun tryRunDrmInterceptor(): Boolean {
        pruneDeadPluginsLocked()
        pruneDeadFactoriesLocked()

        val serviceNames = discoverFactoryServices()
        if (serviceNames.isEmpty()) {
            markHealthy()
            return true
        }

        val initialPids = getServicePids(serviceNames)
        var needsFastRetry = false
        for (name in serviceNames) {
            val existing = factories[name]
            if (existing != null && existing.binder.isBinderAlive) continue

            // getService also gives servicemanager a chance to start a lazy
            // declared HAL before the first application opens MediaDrm.
            val service = ServiceManager.checkService(name) ?: ServiceManager.getService(name) ?: continue
            var control = BinderInterceptor.getBinderControlEndpoint(service)
            val pid = initialPids[name] ?: getServicePids(listOf(name))[name]
            if (control == null) {
                if (pid == null || pid <= 0) {
                    Logger.d("DRM privacy: PID unavailable for $name; will rescan")
                    continue
                }
                when (injectIfDue(pid)) {
                    InjectionResult.SUCCESS -> {
                        control = BinderInterceptor.getBinderControlEndpoint(service)
                        if (control == null) {
                            needsFastRetry = true
                            continue
                        }
                    }
                    InjectionResult.DEFERRED -> continue
                    InjectionResult.FAILED -> continue
                }
            }

            val interceptor = FactoryInterceptor(name)
            val resolvedControl = requireNotNull(control)
            if (
                !BinderInterceptor.registerBinderInterceptor(
                    resolvedControl,
                    service,
                    interceptor,
                    intArrayOf(createDrmPluginTransaction),
                )
            ) {
                Logger.w("DRM privacy: failed to register factory interceptor for $name")
                continue
            }

            val deathRecipient = IBinder.DeathRecipient { onFactoryDied(name, service) }
            try {
                service.linkToDeath(deathRecipient, 0)
            } catch (_: android.os.RemoteException) {
                BinderInterceptor.unregisterBinderInterceptor(resolvedControl, service, interceptor)
                needsFastRetry = true
                continue
            }

            factories[name] =
                FactoryRegistration(
                    name = name,
                    binder = service,
                    pid = pid ?: 0,
                    control = resolvedControl,
                    interceptor = interceptor,
                    deathRecipient = deathRecipient,
                )
            Logger.i("DRM privacy: stable-AIDL factory hook registered for $name")
        }

        markHealthy()
        return !needsFastRetry
    }

    @Synchronized
    fun stopDrmInterceptor(): Boolean {
        var success = true

        val pluginSnapshot = plugins.values.toList()
        plugins.clear()
        for (registration in pluginSnapshot) {
            if (registration.binder.isBinderAlive) {
                success =
                    BinderInterceptor.unregisterBinderInterceptor(
                        registration.control,
                        registration.binder,
                        pluginInterceptor,
                    ) && success
            }
        }

        val factorySnapshot = factories.values.toList()
        factories.clear()
        for (registration in factorySnapshot) {
            if (registration.binder.isBinderAlive) {
                success =
                    BinderInterceptor.unregisterBinderInterceptor(
                        registration.control,
                        registration.binder,
                        registration.interceptor,
                    ) && success
                runCatching { registration.binder.unlinkToDeath(registration.deathRecipient, 0) }
            }
        }

        factorySnapshot.map { it.control }.distinct().forEach { control ->
            success = BinderInterceptor.parkBinderHook(control) && success
        }

        injectedPids.clear()
        lastInjectionAttempt.clear()
        lastReconcileHealthy = false
        lastReconcileMs = 0L
        return success
    }

    @Synchronized
    private fun registerPlugin(
        owner: String,
        plugin: IBinder,
    ) {
        if (plugins.containsKey(plugin)) return
        pruneDeadPluginsLocked()
        if (plugins.size >= MAX_PLUGIN_BINDERS) {
            Logger.w("DRM privacy: plugin registration limit reached")
            return
        }

        val factory = factories[owner] ?: return
        if (
            BinderInterceptor.registerBinderInterceptor(
                factory.control,
                plugin,
                pluginInterceptor,
                intArrayOf(getPropertyByteArrayTransaction),
            )
        ) {
            plugins[plugin] = PluginRegistration(owner, plugin, factory.control)
            Logger.d("DRM privacy: plugin hook registered")
        } else {
            Logger.w("DRM privacy: failed to register plugin interceptor")
        }
    }

    @Synchronized
    private fun onFactoryDied(
        name: String,
        expectedBinder: IBinder,
    ) {
        val registration = factories[name]
        if (registration == null || registration.binder !== expectedBinder) return
        factories.remove(name)
        if (registration.pid > 0) injectedPids.remove(registration.pid)
        removePluginsForOwnerLocked(name)
        lastReconcileHealthy = false
        lastReconcileMs = 0L
        Config.signalRuntimeController()
        Logger.i("DRM privacy: factory restarted; hook reconciliation requested")
    }

    private fun removePluginsForOwnerLocked(owner: String) {
        val iterator = plugins.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.owner == owner) iterator.remove()
        }
    }

    private fun pruneDeadPluginsLocked() {
        val iterator = plugins.entries.iterator()
        while (iterator.hasNext()) {
            if (!iterator.next().key.isBinderAlive) iterator.remove()
        }
    }

    private fun pruneDeadFactoriesLocked() {
        val iterator = factories.entries.iterator()
        while (iterator.hasNext()) {
            val registration = iterator.next().value
            if (!registration.binder.isBinderAlive) {
                iterator.remove()
                if (registration.pid > 0) injectedPids.remove(registration.pid)
                removePluginsForOwnerLocked(registration.name)
            }
        }
    }

    private enum class InjectionResult {
        SUCCESS,
        DEFERRED,
        FAILED,
    }

    private fun injectIfDue(pid: Int): InjectionResult {
        val now = SystemClock.elapsedRealtime()
        val previous = lastInjectionAttempt[pid]
        if (previous != null && now - previous in 0 until INJECTION_RETRY_INTERVAL_MS) {
            return InjectionResult.DEFERRED
        }
        lastInjectionAttempt[pid] = now

        val symbol = if (injectedPids.contains(pid)) "resume" else "entry"
        val modulePath = getModuleDir()
        return try {
            val process =
                ProcessBuilder(
                    "$modulePath/inject",
                    pid.toString(),
                    "$modulePath/libcleverestricky.so",
                    symbol,
                ).redirectOutput(File("/dev/null"))
                    .redirectError(File("/dev/null"))
                    .start()
            val completed = process.waitFor(INJECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                Logger.w("DRM privacy: injector timed out for pid=$pid")
                InjectionResult.FAILED
            } else if (process.exitValue() != 0) {
                Logger.w("DRM privacy: injector failed for pid=$pid (exit=${process.exitValue()})")
                InjectionResult.FAILED
            } else {
                injectedPids.add(pid)
                Logger.i("DRM privacy: native Binder hook activated for DRM HAL pid=$pid")
                InjectionResult.SUCCESS
            }
        } catch (error: Exception) {
            Logger.e("DRM privacy: failed to run injector for pid=$pid", error)
            InjectionResult.FAILED
        }
    }

    private fun discoverFactoryServices(): List<String> {
        val names = LinkedHashSet<String>()

        runCatching {
            val method = ServiceManager::class.java.getDeclaredMethod("getDeclaredInstances", String::class.java)
            method.isAccessible = true
            val instances = method.invoke(null, DRM_FACTORY_DESCRIPTOR) as? kotlin.Array<*>
            instances?.forEach { instance ->
                val name = instance as? String
                if (!name.isNullOrBlank()) names += "$DRM_FACTORY_PREFIX$name"
            }
        }

        runCatching { ServiceManager.listServices() }
            .getOrNull()
            ?.asSequence()
            ?.filter { it.startsWith(DRM_FACTORY_PREFIX) }
            ?.forEach(names::add)

        return names.asSequence().filter(::isSafeFactoryName).sorted().take(MAX_FACTORY_SERVICES).toList()
    }

    private fun isSafeFactoryName(name: String): Boolean {
        if (!name.startsWith(DRM_FACTORY_PREFIX) || name.length > 192) return false
        val instance = name.substring(DRM_FACTORY_PREFIX.length)
        return instance.isNotEmpty() &&
            instance.all { character ->
                character.isLetterOrDigit() || character == '_' || character == '-' || character == '.'
            }
    }

    private fun getServicePids(serviceNames: List<String>): Map<String, Int> {
        if (serviceNames.isEmpty()) return emptyMap()
        val wanted = serviceNames.toHashSet()
        val result = HashMap<String, Int>(wanted.size)
        runCatching {
            val method = ServiceManager::class.java.getDeclaredMethod("getServiceDebugInfo")
            method.isAccessible = true
            val debugArray = method.invoke(null) ?: return@runCatching
            val length = ReflectArray.getLength(debugArray)
            for (index in 0 until length) {
                val item = ReflectArray.get(debugArray, index) ?: continue
                val clazz = item.javaClass
                val name = readField(clazz, item, "name") as? String ?: continue
                if (name !in wanted) continue
                val pid =
                    (readField(clazz, item, "debugPid") as? Number)?.toInt()
                        ?: (readField(clazz, item, "pid") as? Number)?.toInt()
                        ?: continue
                if (pid > 0) result[name] = pid
            }
        }.onFailure { error ->
            Logger.d("DRM privacy: servicemanager debug PID lookup unavailable (${error.javaClass.simpleName})")
        }
        return result
    }

    private fun readField(
        clazz: Class<*>,
        instance: Any,
        name: String,
    ): Any? =
        runCatching {
            clazz.getDeclaredField(name).apply { isAccessible = true }.get(instance)
        }.getOrNull()

    private fun markHealthy() {
        lastReconcileMs = SystemClock.elapsedRealtime()
        lastReconcileHealthy = true
    }

    private class FactoryInterceptor(
        private val owner: String,
    ) : BinderInterceptor() {
        override fun onPreTransact(
            target: IBinder,
            code: Int,
            flags: Int,
            callingUid: Int,
            callingPid: Int,
            data: Parcel,
        ): Result = if (code == createDrmPluginTransaction) Continue else Skip

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
            if (code != createDrmPluginTransaction || reply == null || resultCode != 0) return Skip
            val originalPosition = reply.dataPosition()
            try {
                reply.readException()
                val plugin = reply.readStrongBinder() ?: return Skip
                registerPlugin(owner, plugin)
            } catch (_: RuntimeException) {
                // A vendor that is not wire-compatible with the frozen AIDL
                // shape is left completely untouched.
            } finally {
                reply.setDataPosition(originalPosition)
            }
            return Skip
        }
    }

    private class PluginInterceptor : BinderInterceptor() {
        override fun onPreTransact(
            target: IBinder,
            code: Int,
            flags: Int,
            callingUid: Int,
            callingPid: Int,
            data: Parcel,
        ): Result {
            if (code != getPropertyByteArrayTransaction || !shouldProtectUid(callingUid)) return Skip
            return if (readPropertyName(data) == DEVICE_UNIQUE_ID) Continue else Skip
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
            if (
                code != getPropertyByteArrayTransaction ||
                reply == null ||
                resultCode != 0 ||
                !shouldProtectUid(callingUid) ||
                readPropertyName(data) != DEVICE_UNIQUE_ID
            ) {
                return Skip
            }

            val originalPosition = reply.dataPosition()
            var original: ByteArray? = null
            var pseudonym: ByteArray? = null
            return try {
                reply.readException()
                original = reply.createByteArray() ?: return Skip
                val length = original.size
                if (length !in DrmPrivacyIdentity.MIN_IDENTIFIER_BYTES..DrmPrivacyIdentity.MAX_IDENTIFIER_BYTES) {
                    return Skip
                }
                pseudonym = DrmPrivacyIdentity.idForUid(callingUid, length) ?: return Skip

                Parcel.obtain().let { replacement ->
                    replacement.writeNoException()
                    replacement.writeByteArray(pseudonym)
                    OverrideReply(0, replacement)
                }
            } catch (_: RuntimeException) {
                Skip
            } finally {
                original?.fill(0)
                pseudonym?.fill(0)
                reply.setDataPosition(originalPosition)
            }
        }

        private fun readPropertyName(data: Parcel): String? {
            val originalPosition = data.dataPosition()
            return try {
                data.enforceInterface(DRM_PLUGIN_DESCRIPTOR)
                data.readString()
            } catch (_: RuntimeException) {
                null
            } finally {
                data.setDataPosition(originalPosition)
            }
        }

        private fun shouldProtectUid(uid: Int): Boolean =
            Config.isSpoofEnabled && Config.getAppPrivacyMode(uid) == Config.AppPrivacyMode.ISOLATE
    }

    private fun resolveTransactionCode(
        stubClassName: String,
        methodName: String,
        fallback: Int,
    ): Int {
        val reflected =
            runCatching {
                val stub = Class.forName(stubClassName)
                getTransactCode(stub, methodName)
            }.getOrNull()
        return reflected?.takeIf { it > 0 } ?: fallback
    }
}
