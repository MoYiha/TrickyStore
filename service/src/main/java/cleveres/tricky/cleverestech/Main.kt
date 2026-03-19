package cleveres.tricky.cleverestech

import android.system.Os
import cleveres.tricky.cleverestech.rkp.LocalRkpProxy
import cleveres.tricky.cleverestech.util.KeyboxAutoCleaner
import cleveres.tricky.cleverestech.util.SecureFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*

private const val CONFIG_DIR_MODE = 448
private const val RKP_KEY_MODE = 384

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
        val server = WebServer(WEB_UI_PORT, configDir)
        Logger.d("Main: Starting WebUI server bootstrap on requested port $WEB_UI_PORT")
        server.start()
        val port = server.listeningPort
        val token = server.token
        Logger.i("Web server started on port $port")
        Logger.d("Main: WebUI server is listening on $WEB_UI_LOOPBACK_HOST:$port (tokenLength=${token.length})")
        val portFile = File(configDir, "web_port")
        // Secure directory before writing sensitive file
        try {
            SecureFile.mkdirs(configDir, CONFIG_DIR_MODE) // 0700
            Logger.d("Main: Ensured WebUI config directory permissions for ${configDir.absolutePath}")
        } catch (t: Throwable) {
            Logger.e("failed to set permissions for config dir", t)
        }

        // Initialize RKP Proxy and ensure key is accessible by system/interceptor
        try {
            LocalRkpProxy.getMacKey()
            Os.chmod(LocalRkpProxy.KEY_FILE_PATH, RKP_KEY_MODE) // 0600
        } catch (t: Throwable) {
            Logger.e("failed to init RKP permissions", t)
        }

        SecureFile.writeText(portFile, "$port|$token")
        Logger.d("Main: Wrote WebUI port metadata to ${portFile.absolutePath}")
    } catch (e: Exception) {
        Logger.e("Failed to start web server", e)
    }

    runBlocking {
        while (true) {
            var ksSuccess = false
            var telSuccess = false

            // Launch concurrent polling for both interceptors to improve startup time
            val ksJob = launch(Dispatchers.IO) { ksSuccess = KeystoreInterceptor.tryRunKeystoreInterceptor() }
            val telJob = launch(Dispatchers.IO) { telSuccess = TelephonyInterceptor.tryRunTelephonyInterceptor() }

            ksJob.join()
            telJob.join()

            if (!ksSuccess) {
                // Keystore is critical, so we loop until it's ready
                try {
                    delay(1000)
                } catch (_: CancellationException) {
                    Thread.currentThread().interrupt()
                    Logger.i("Main: Keystore wait interrupted, shutting down")
                    return@runBlocking
                }
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
                val telRetryJob = launch(Dispatchers.IO) {
                    if (!TelephonyInterceptor.tryRunTelephonyInterceptor()) {
                        Logger.d("Retrying Telephony Interceptor injection...")
                    }
                }

                val drmRetryJob = launch(Dispatchers.IO) {
                    if (!drmRegistered) {
                        drmRegistered = DrmInterceptor.tryRunDrmInterceptor()
                    }
                }

                val rkpJob = launch(Dispatchers.IO) {
                    LocalRkpProxy.checkAndRotate()
                }

                telRetryJob.join()
                drmRetryJob.join()
                rkpJob.join()

                try {
                    delay(10000)
                } catch (_: CancellationException) {
                    Thread.currentThread().interrupt()
                    Logger.i("Main: Poll loop interrupted, shutting down")
                    return@runBlocking
                }
            }
        }
    }
}
