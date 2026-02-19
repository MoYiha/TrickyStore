package cleveres.tricky.cleverestech

import android.os.Binder
import android.os.Parcel
import cleveres.tricky.cleverestech.Config

class PropertyHiderService : Binder() {
    companion object {
        // Must match the C++ side: IBinder.FIRST_CALL_TRANSACTION + 0
        const val GET_SPOOFED_PROPERTY_TRANSACTION_CODE = FIRST_CALL_TRANSACTION + 0
        // Must match the C++ side
        const val INTERFACE_TOKEN = "android.os.IPropertyServiceHider"

        // Block internal configuration keys from being exposed
        private val BLOCKED_KEYS = setOf(
            "CODENAME", "FINGERPRINT", "SECURITY_PATCH", "MODEL", "BRAND", "MANUFACTURER",
            "DEVICE", "PRODUCT", "ID", "DISPLAY", "RELEASE", "INCREMENTAL", "TYPE", "TAGS",
            "BOOTLOADER", "BOARD", "HARDWARE", "HOST", "USER", "TIMESTAMP", "SDK_INT",
            "PREVIEW_SDK", "TEMPLATE", "MODULE_HASH", "ATTESTATION_VERSION", "KEYMINT_VERSION"
        )
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == GET_SPOOFED_PROPERTY_TRANSACTION_CODE) {
            data.enforceInterface(INTERFACE_TOKEN) // Verify the token
            val propertyName = data.readString()
            reply?.writeNoException() // Important: write no exception before writing result

            if (propertyName != null) {
                // Security: Prevent exposure of internal configuration variables
                if (propertyName.startsWith("ATTESTATION_ID_") || BLOCKED_KEYS.contains(propertyName)) {
                    if (BuildConfig.DEBUG) {
                        Logger.d("PropertyHiderService: Blocked access to sensitive config '$propertyName' from ${Binder.getCallingUid()}")
                    }
                    reply?.writeString(null)
                    return true
                }

                // Use getBuildVar as it holds the loaded properties from spoof_build_vars
                val callingUid = Binder.getCallingUid()
                val spoofedValue = Config.getBuildVar(propertyName, callingUid)
                if (BuildConfig.DEBUG) {
                    Logger.d("PropertyHiderService: Received request for '$propertyName' from $callingUid, spoofed to '$spoofedValue'")
                }
                reply?.writeString(spoofedValue) // writeString can handle null
            } else {
                if (BuildConfig.DEBUG) {
                    Logger.d("PropertyHiderService: Received request with null property name")
                }
                reply?.writeString(null) // Property name was null
            }
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }
}
