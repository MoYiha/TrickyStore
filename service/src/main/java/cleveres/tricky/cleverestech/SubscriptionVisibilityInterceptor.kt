package cleveres.tricky.cleverestech

import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.telephony.SubscriptionInfo
import cleveres.tricky.cleverestech.binder.BinderInterceptor
import com.android.internal.telephony.ISub

object SubscriptionVisibilityInterceptor : BinderInterceptor() {
    private val getActiveSubscriptionInfoListTransaction =
        getTransactCode(ISub.Stub::class.java, "getActiveSubscriptionInfoList")
    private val getActiveSubInfoCountTransaction =
        getTransactCode(ISub.Stub::class.java, "getActiveSubInfoCount")
    private val getActiveSubInfoCountMaxTransaction =
        getTransactCode(ISub.Stub::class.java, "getActiveSubInfoCountMax")
    private val interceptedCodes =
        validTransactCodes(
            getActiveSubscriptionInfoListTransaction,
            getActiveSubInfoCountTransaction,
            getActiveSubInfoCountMaxTransaction,
        )

    private var subscriptionService: IBinder? = null
    private var controlEndpoint: IBinder? = null

    @Volatile
    private var registered = false

    override fun onPreTransact(
        target: IBinder,
        code: Int,
        flags: Int,
        callingUid: Int,
        callingPid: Int,
        data: Parcel,
    ): Result =
        if (
            registered &&
            target === subscriptionService &&
            code in interceptedCodes &&
            Config.shouldInterceptSubscriptionVisibility &&
            Config.getVisibleSimCount(callingUid) != null
        ) {
            Continue
        } else {
            Skip
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
            !registered ||
            target !== subscriptionService ||
            code !in interceptedCodes ||
            reply == null ||
            resultCode != 0 ||
            !Config.shouldInterceptSubscriptionVisibility
        ) {
            return Skip
        }
        val limit = Config.getVisibleSimCount(callingUid) ?: return Skip
        val originalPosition = reply.dataPosition()
        return try {
            reply.readException()
            when (code) {
                getActiveSubscriptionInfoListTransaction -> {
                    val original = reply.createTypedArrayList(SubscriptionInfo.CREATOR) ?: return Skip
                    val visible = boundedVisibleSubscriptions(original, limit)
                    if (visible.size == original.size) return Skip
                    Parcel.obtain().also { replacement ->
                        replacement.writeNoException()
                        replacement.writeTypedList(visible)
                    }.let { OverrideReply(0, it) }
                }
                getActiveSubInfoCountTransaction, getActiveSubInfoCountMaxTransaction -> {
                    val original = reply.readInt()
                    val visible = boundedVisibleSubscriptionCount(original, limit)
                    if (visible == original) return Skip
                    Parcel.obtain().also { replacement ->
                        replacement.writeNoException()
                        replacement.writeInt(visible)
                    }.let { OverrideReply(0, it) }
                }
                else -> Skip
            }
        } catch (_: RuntimeException) {
            Skip
        } finally {
            reply.setDataPosition(originalPosition)
        }
    }

    fun tryRun(): Boolean {
        if (!Config.shouldInterceptSubscriptionVisibility) {
            stop()
            return true
        }
        
        synchronized(this) {
            val current = subscriptionService
            if (registered && current != null && current.isBinderAlive) return true
            registered = false
        }

        val service = ServiceManager.getService("isub") ?: return false
        val control = getBinderControlEndpoint(service) ?: return false
        
        if (!registerBinderInterceptor(control, service, this, interceptedCodes)) return false
        
        synchronized(this) {
            subscriptionService = service
            controlEndpoint = control
            registered = true
        }
        
        if (!Config.shouldInterceptSubscriptionVisibility) {
            stop()
            return true
        }
        Logger.i("Subscription visibility interceptor registered")
        return true
    }

    fun isRunning(): Boolean =
        synchronized(this) { registered && subscriptionService?.isBinderAlive == true }

    fun stop(): Boolean {
        var target: IBinder? = null
        var control: IBinder? = null
        synchronized(this) {
            if (!registered) {
                subscriptionService = null
                controlEndpoint = null
                return true
            }
            target = subscriptionService
            control = controlEndpoint
        }
        
        if (target == null || control == null || !target!!.isBinderAlive) {
            synchronized(this) {
                registered = false
                subscriptionService = null
                controlEndpoint = null
            }
            return true
        }
        
        if (!unregisterBinderInterceptor(control!!, target!!, this)) return false
        
        synchronized(this) {
            registered = false
            subscriptionService = null
            controlEndpoint = null
        }
        return true
    }

    override fun onInterceptorReplaced() {
        synchronized(this) {
            registered = false
            subscriptionService = null
            controlEndpoint = null
        }
        Config.signalRuntimeController()
    }
}
