package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.KeyboxAutoCleaner
import cleveres.tricky.cleverestech.util.SecureFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

private const val CONFIG_DIR_MODE = 448

fun main(args: Array<String>) {
    Logger.i("Welcome to Service!")
    val isTampered =
        try {
            !Verification.check()
        } catch (error: Exception) {
            Logger.e("Module verification failed unexpectedly", error)
            true
        }
    if (isTampered) {
        Logger.e("TAMPER DETECTED: Disabling all interceptors and running in safe mode.")
    }
    runBlocking {
        val configDir = File("/data/adb/cleverestricky")
        try {
            SecureFile.mkdirs(configDir, CONFIG_DIR_MODE)
        } catch (e: Exception) {
            Logger.e("Failed to prepare configuration directory", e)
            return@runBlocking
        }

        if (isTampered) {
            runCatching { WebUiBridge(WebServer(0, configDir, true), configDir).start() }
                .onFailure { Logger.e("Failed to start native WebUI lockdown endpoint", it) }
            Logger.e("Main: Running in tamper lockdown; native interceptors will not be registered")
            while (true) {
                delay(60000)
            }
        }

        try {
            Config.initialize()
            BootLogic.run()
        } catch (e: Exception) {
            Logger.e("Failed to initialize Config/BootLogic", e)
            Logger.e("Main: Exiting so the module supervisor can retry initialization")
            return@runBlocking
        }

        try {
            WebUiBridge(WebServer(0, configDir), configDir).start()
        } catch (e: Exception) {
            Logger.e("Failed to start native WebUI bridge", e)
            Logger.e("Main: Exiting so the module supervisor can restore native WebUI service")
            return@runBlocking
        }

        KeyboxAutoCleaner.start()

        var previousEngineState: Boolean? = null
        var previousTelephonyState: Boolean? = null
        var engineStopPending = false
        var telephonyStopPending = false
        while (true) {
            val engineEnabled = Config.isSpoofEnabled
            if (!engineEnabled) {
                if (previousEngineState != false || engineStopPending) {
                    val wasPending = engineStopPending
                    val telephonyStopped = TelephonyInterceptor.stopTelephonyInterceptor()
                    val keystoreStopped = KeystoreInterceptor.stopKeystoreInterceptor()
                    engineStopPending = !telephonyStopped || !keystoreStopped
                    if (engineStopPending) {
                        if (!wasPending) Logger.w("Spoof Engine cleanup is incomplete; retry scheduled")
                    } else {
                        telephonyStopPending = false
                        Logger.i("Spoof Engine paused; Binder hooks are parked")
                    }
                }
                previousEngineState = if (engineStopPending) null else false
                previousTelephonyState = Config.shouldInterceptTelephony
                try {
                    Config.awaitRuntimeController(if (engineStopPending) 1_000 else 30_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@runBlocking
                }
                continue
            }

            engineStopPending = false
            if (previousEngineState == false) {
                Logger.i("Spoof Engine resumed; restoring configured Binder interceptors")
            }
            previousEngineState = true

            var ksSuccess = KeystoreInterceptor.isRunning()
            var telSuccess = !Config.shouldInterceptTelephony || TelephonyInterceptor.isRunning()

            val ksJob =
                if (!ksSuccess) {
                    launch(Dispatchers.IO) {
                        try {
                            ksSuccess = KeystoreInterceptor.tryRunKeystoreInterceptor()
                        } catch (e: Exception) {
                            Logger.e("Keystore interceptor threw unexpected exception", e)
                        }
                    }
                } else {
                    null
                }
            val telJob =
                if (!telSuccess) {
                    launch(Dispatchers.IO) {
                        try {
                            if (Config.shouldInterceptTelephony) {
                                telSuccess = TelephonyInterceptor.tryRunTelephonyInterceptor()
                            }
                        } catch (e: Exception) {
                            Logger.e("Telephony interceptor threw unexpected exception", e)
                        }
                    }
                } else {
                    null
                }

            ksJob?.join()
            telJob?.join()

            val telephonyEnabled = Config.shouldInterceptTelephony
            if (!telephonyEnabled && (previousTelephonyState != false || telephonyStopPending)) {
                val wasPending = telephonyStopPending
                telephonyStopPending = !TelephonyInterceptor.stopTelephonyInterceptor()
                if (telephonyStopPending && !wasPending) {
                    Logger.w("Telephony hook cleanup is incomplete; retry scheduled")
                }
            } else if (telephonyEnabled) {
                telephonyStopPending = false
            }
            previousTelephonyState = if (telephonyStopPending) null else telephonyEnabled

            if (!ksSuccess) Logger.d("Keystore interceptor is not ready; retry scheduled")
            if (!telSuccess) {
                Logger.d("Telephony interceptor not ready yet")
            }

            try {
                Config.awaitRuntimeController(
                    if (ksSuccess && telSuccess && !telephonyStopPending) 30_000 else 1_000,
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                Logger.i("Main: Runtime controller interrupted, shutting down")
                return@runBlocking
            }
        }
    }
}
