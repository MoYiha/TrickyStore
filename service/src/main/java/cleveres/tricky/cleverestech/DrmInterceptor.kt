package cleveres.tricky.cleverestech

import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import cleveres.tricky.cleverestech.binder.BinderInterceptor
import java.io.File

object DrmInterceptor : BinderInterceptor() {

    private var drmBinder: IBinder? = null
    private var triedCount = 0
    private var injected = false

    private const val TRANSACTION_GET_PROPERTY_STRING = 17
    private const val TRANSACTION_GET_PROPERTY_BYTE_ARRAY = 18

    override fun onPostTransact(
        target: IBinder,
        code: Int,
        flags: Int,
        callingUid: Int,
        callingPid: Int,
        data: Parcel,
        reply: Parcel?,
        resultCode: Int
    ): Result {
        if (reply == null) return Skip
        if (!Config.isDrmFixEnabled()) return Skip

        when (code) {
            TRANSACTION_GET_PROPERTY_STRING -> {
                return handleGetPropertyString(reply, callingUid)
            }
            TRANSACTION_GET_PROPERTY_BYTE_ARRAY -> {
                return handleGetPropertyByteArray(reply, callingUid)
            }
        }
        return Skip
    }

    private fun handleGetPropertyString(reply: Parcel, callingUid: Int): Result {
        val pos = reply.dataPosition()
        if (kotlin.runCatching { reply.readException() }.exceptionOrNull() != null) {
            reply.setDataPosition(pos)
            return Skip
        }
        val originalValue = reply.readString() ?: return Skip

        val securityLevel = Config.getBuildVar("ro.com.google.widevine.level") ?: "1"

        if (originalValue == "L3" || originalValue == "L2") {
            val spoofedLevel = "L$securityLevel"
            Logger.i("DRM Intercept: Spoofing securityLevel $originalValue -> $spoofedLevel for uid=$callingUid")
            val p = Parcel.obtain()
            p.writeNoException()
            p.writeString(spoofedLevel)
            return OverrideReply(0, p)
        }

        return Skip
    }

    private fun handleGetPropertyByteArray(reply: Parcel, callingUid: Int): Result {
        val pos = reply.dataPosition()
        if (kotlin.runCatching { reply.readException() }.exceptionOrNull() != null) {
            reply.setDataPosition(pos)
            return Skip
        }

        val randomDrmOnBoot = File("/data/adb/cleverestricky/random_drm_on_boot").exists()
        if (randomDrmOnBoot) {
            val spoofedId = ByteArray(32)
            java.security.SecureRandom().nextBytes(spoofedId)
            Logger.i("DRM Intercept: Spoofing deviceUniqueId for uid=$callingUid")
            val p = Parcel.obtain()
            p.writeNoException()
            p.writeByteArray(spoofedId)
            return OverrideReply(0, p)
        }

        return Skip
    }

    private fun findDrmServicePid(): Int? {
        val targetNames = listOf(
            "android.hardware.drm-service.widevine",
            "android.hardware.drm-service.clearkey",
            "android.hardware.drm@1.4-service.widevine",
            "android.hardware.drm@1.3-service.widevine",
            "mediadrmserver"
        )
        val proc = File("/proc")
        if (!proc.exists() || !proc.isDirectory) return null

        val files = proc.listFiles() ?: return null
        for (f in files) {
            if (!f.isDirectory) continue
            val name = f.name
            if (name.all { it.isDigit() }) {
                kotlin.runCatching {
                    val cmdlineFile = File(f, "cmdline")
                    if (cmdlineFile.exists()) {
                        val cmdline = cmdlineFile.readBytes()
                        var end = 0
                        while (end < cmdline.size && cmdline[end] != 0.toByte()) {
                            end++
                        }
                        val argv0 = String(cmdline, 0, end)
                        for (target in targetNames) {
                            if (argv0 == target || argv0.endsWith("/$target")) {
                                return name.toInt()
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun findDrmService(): IBinder? {
        val serviceNames = listOf(
            "drm.IDrmFactory/widevine",
            "android.hardware.drm.IDrmFactory/widevine",
            "media.drm"
        )
        for (name in serviceNames) {
            val b = kotlin.runCatching { ServiceManager.getService(name) }.getOrNull()
            if (b != null) return b
        }
        return null
    }

    fun tryRunDrmInterceptor(): Boolean {
        if (!Config.isDrmFixEnabled()) return false

        Logger.i("trying to register DRM interceptor ($triedCount) ...")

        val b = findDrmService()
        if (b == null) {
            Logger.d("DRM service not found, will retry")
            triedCount += 1
            return false
        }

        val bd = getBinderBackdoor(b)
        if (bd == null) {
            if (triedCount >= 3) {
                Logger.e("DRM: tried injection but still has no backdoor, skipping")
                return false
            }

            if (!injected) {
                Logger.i("DRM: trying to inject DRM service process ...")
                val pid = findDrmServicePid()
                if (pid == null) {
                    Logger.e("DRM: failed to find DRM service pid!")
                    triedCount += 1
                    return false
                }

                val modulePath = "/data/adb/modules/cleverestricky"
                val p = Runtime.getRuntime().exec(
                    arrayOf(
                        "$modulePath/inject",
                        pid.toString(),
                        "$modulePath/libcleverestricky.so",
                        "entry"
                    )
                )
                try {
                    // Drain streams to prevent FD exhaustion
                    p.inputStream.readBytes()
                    p.errorStream.readBytes()
                } catch (_: Exception) {}
                if (p.waitFor() != 0) {
                    Logger.e("DRM: failed to inject!")
                } else {
                    Logger.i("DRM: injected successfully")
                    injected = true
                }
            }
            triedCount += 1
            return false
        }

        drmBinder = b
        Logger.i("register for DRM service!")
        registerBinderInterceptor(bd, b, this)
        b.linkToDeath({
            Logger.e("DRM service died! Resetting injection state.")
            injected = false
            triedCount = 0
            drmBinder = null
        }, 0)

        return true
    }
}
