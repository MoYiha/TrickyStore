package cleveres.tricky.cleverestech

import android.system.Os
import cleveres.tricky.cleverestech.rkp.LocalRkpProxy
import cleveres.tricky.cleverestech.util.KeyboxAutoCleaner
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Logger.i("Welcome to CleveresTricky!")
    Verification.check()

    // Start Auto Cleaner
    KeyboxAutoCleaner.start()

    // Start Web Server
    try {
        val configDir = File("/data/adb/cleverestricky")
        val server = WebServer(0, configDir) // Random port
        server.start()
        val port = server.listeningPort
        val token = server.token
        Logger.i("Web server started on port $port")
        val portFile = File(configDir, "web_port")
        if (!configDir.exists()) configDir.mkdirs()
        // Secure directory before writing sensitive file
        try {
            Os.chmod(configDir.absolutePath, 448) // 0700
        } catch (t: Throwable) {
            Logger.e("failed to set permissions for config dir", t)
        }

        // Initialize RKP Proxy and ensure key is accessible by system/interceptor
        try {
            LocalRkpProxy.getMacKey()
            Os.chmod(LocalRkpProxy.KEY_FILE_PATH, 438) // 0666
        } catch (t: Throwable) {
            Logger.e("failed to init RKP permissions", t)
        }

        portFile.writeText("$port|$token")
        portFile.setReadable(false, false) // Clear all
        portFile.setReadable(true, true) // Owner only (0600)
    } catch (e: Exception) {
        Logger.e("Failed to start web server", e)
    }

    while (true) {
        val ksSuccess = KeystoreInterceptor.tryRunKeystoreInterceptor()
        val telSuccess = TelephonyInterceptor.tryRunTelephonyInterceptor()

        if (!ksSuccess) {
            // Keystore is critical, so we loop until it's ready
            Thread.sleep(1000)
            continue
        }

        // Telephony is optional/advanced, but we should try to keep it alive
        // Since injecting into com.android.phone might take time to start up
        if (!telSuccess) {
             Logger.d("Telephony interceptor not ready yet")
        }

        Config.initialize()
        while (true) {
            // Periodically check Telephony status in case com.android.phone crashes/restarts
            if (!TelephonyInterceptor.tryRunTelephonyInterceptor()) {
                 Logger.i("Retrying Telephony Interceptor injection...")
            }
            Thread.sleep(10000)
        }
    }
}
