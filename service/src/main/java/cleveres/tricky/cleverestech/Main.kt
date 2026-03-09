package cleveres.tricky.cleverestech

import android.system.Os
import cleveres.tricky.cleverestech.rkp.LocalRkpProxy
import cleveres.tricky.cleverestech.util.KeyboxAutoCleaner
import cleveres.tricky.cleverestech.util.SecureFile
import java.io.File
import java.security.MessageDigest

fun main(args: Array<String>) {
    Logger.i("Welcome to Service!")
    Verification.check()

    // Start Auto Cleaner
    KeyboxAutoCleaner.start()

    // Start Keybox Fetcher
    cleveres.tricky.cleverestech.util.KeyboxFetcher.schedule()

    // Start Web Server
    try {
        val configDir = File("/data/adb/cleverestricky")
        Logger.d("Main: Preparing WebUI config directory at ${configDir.absolutePath}")
        val server = WebServer(5623, configDir) // Fixed port 5623
        Logger.d("Main: Starting WebUI server bootstrap on requested port 5623")
        server.start()
        val port = server.listeningPort
        val token = server.token
        Logger.i("Web server started on port $port")
        Logger.d("Main: WebUI server is listening on 127.0.0.1:$port (tokenLength=${token.length})")
        val portFile = File(configDir, "web_port")
        // Secure directory before writing sensitive file
        try {
            SecureFile.mkdirs(configDir, 448) // 0700
            Logger.d("Main: Ensured WebUI config directory permissions for ${configDir.absolutePath}")
        } catch (t: Throwable) {
            Logger.e("failed to set permissions for config dir", t)
        }

        // Initialize RKP Proxy and ensure key is accessible by system/interceptor
        try {
            LocalRkpProxy.getMacKey()
            Os.chmod(LocalRkpProxy.KEY_FILE_PATH, 384) // 0600
        } catch (t: Throwable) {
            Logger.e("failed to init RKP permissions", t)
        }

        SecureFile.writeText(portFile, "$port|$token")
        Logger.d("Main: Wrote WebUI port metadata to ${portFile.absolutePath}")
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
        BootLogic.run()

        var drmRegistered = false
        while (true) {
            if (!TelephonyInterceptor.tryRunTelephonyInterceptor()) {
                 Logger.d("Retrying Telephony Interceptor injection...")
            }
            if (!drmRegistered) {
                drmRegistered = DrmInterceptor.tryRunDrmInterceptor()
            }
            LocalRkpProxy.checkAndRotate()
            Thread.sleep(10000)
        }
    }
}
