package cleveres.tricky.cleverestech

import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.ServiceManager
import cleveres.tricky.cleverestech.binder.BinderInterceptor
import com.android.internal.telephony.IPhoneSubInfo
import java.io.File
import kotlin.system.exitProcess

object TelephonyInterceptor : BinderInterceptor() {

    private val getDeviceIdTransaction = getTransactCode(IPhoneSubInfo.Stub::class.java, "getDeviceId")
    private val getDeviceIdForPhoneTransaction = getTransactCode(IPhoneSubInfo.Stub::class.java, "getDeviceIdForPhone")
    private val getImeiForSubscriberTransaction = getTransactCode(IPhoneSubInfo.Stub::class.java, "getImeiForSubscriber")

    private val getSubscriberIdTransaction = getTransactCode(IPhoneSubInfo.Stub::class.java, "getSubscriberId")
    private val getSubscriberIdForSubscriberTransaction = getTransactCode(IPhoneSubInfo.Stub::class.java, "getSubscriberIdForSubscriber")

    private val getIccSerialNumberTransaction = getTransactCode(IPhoneSubInfo.Stub::class.java, "getIccSerialNumber")
    private val getIccSerialNumberForSubscriberTransaction = getTransactCode(IPhoneSubInfo.Stub::class.java, "getIccSerialNumberForSubscriber")

    private lateinit var iphonesubinfo: IBinder
    private var triedCount = 0
    private var injected = false

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

        // Only intercept if we have valid configs and global spoofing is on or targeted
        // For simplicity in this advanced feature, we stick to Global Mode or "System-wide" flag
        // Assuming Config.needHack(callingUid) covers it.

        if (!Config.needHack(callingUid)) return Skip

        // Read exception code
        val pos = reply.dataPosition()
        if (kotlin.runCatching { reply.readException() }.exceptionOrNull() != null) {
            reply.setDataPosition(pos)
            return Skip
        }

        val imei = Config.getBuildVar("ATTESTATION_ID_IMEI")
        val imei2 = Config.getBuildVar("ATTESTATION_ID_IMEI2")
        val imsi = Config.getBuildVar("ATTESTATION_ID_IMSI") // We need to add this to Config/RandomUtils
        val iccid = Config.getBuildVar("ATTESTATION_ID_ICCID") // And this

        var spoofedVal: String? = null

        if (code == getDeviceIdTransaction || code == getDeviceIdForPhoneTransaction || code == getImeiForSubscriberTransaction) {
             // Simple heuristic: if requesting secondary phone, return imei2, else imei
             // However, transaction args analysis is hard without unmarshalling.
             // We'll just return the primary IMEI for now as a "Global" override.
             spoofedVal = imei
        } else if (code == getSubscriberIdTransaction || code == getSubscriberIdForSubscriberTransaction) {
             spoofedVal = imsi
        } else if (code == getIccSerialNumberTransaction || code == getIccSerialNumberForSubscriberTransaction) {
             spoofedVal = iccid
        }

        if (spoofedVal != null) {
             Logger.i("Intercepted Telephony: code=$code uid=$callingUid -> Spoofing $spoofedVal")
             val p = Parcel.obtain()
             p.writeNoException()
             p.writeString(spoofedVal)
             return OverrideReply(0, p)
        }

        return Skip
    }

    private fun findPhoneProcessPid(): Int? {
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
                        if (argv0 == "com.android.phone") {
                            return name.toInt()
                        }
                    }
                }
            }
        }
        return null
    }

    fun tryRunTelephonyInterceptor(): Boolean {
        if (injected) return true // Assume success if injected once? Or we need to re-check binder?

        Logger.i("trying to register telephony interceptor ($triedCount) ...")

        // "iphonesubinfo" is the standard service name for IPhoneSubInfo
        val b = ServiceManager.getService("iphonesubinfo")
        if (b == null) {
            Logger.e("iphonesubinfo service not found")
            return false
        }

        val bd = getBinderBackdoor(b)
        if (bd == null) {
             if (triedCount >= 3) {
                Logger.e("Telephony: tried injection but still has no backdoor, skipping")
                return false
            }

            if (!injected) {
                Logger.i("Telephony: trying to inject com.android.phone ...")
                val pid = findPhoneProcessPid()
                if (pid == null) {
                    Logger.e("Telephony: failed to find com.android.phone pid!")
                    triedCount += 1
                    return false
                }

                // Use the same injection binary and lib
                val p = Runtime.getRuntime().exec(
                    arrayOf(
                        "./inject",
                        pid.toString(),
                        "libtricky_store.so",
                        "entry"
                    )
                )
                if (p.waitFor() != 0) {
                    Logger.e("Telephony: failed to inject!")
                    // Do not exitProcess here, as we are the main daemon and might be handling Keystore fine
                } else {
                    Logger.i("Telephony: injected successfully")
                    injected = true
                }
            }
            triedCount += 1
            return false
        }

        iphonesubinfo = b
        Logger.i("register for iphonesubinfo!")
        registerBinderInterceptor(bd, b, this)
        iphonesubinfo.linkToDeath({
             Logger.e("iphonesubinfo died! Resetting injection state.")
             injected = false
             triedCount = 0
        }, 0)

        return true
    }
}
