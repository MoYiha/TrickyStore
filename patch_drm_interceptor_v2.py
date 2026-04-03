import re

file_path = "service/src/main/java/cleveres/tricky/cleverestech/DrmInterceptor.kt"

with open(file_path, 'r') as f:
    content = f.read()

# Eagerly set injected to true to prevent process stampedes.
# If injection fails, set it back to false.

# Also, we should add an `isInjecting` or just rely on `injected`.
# If we set `injected = true` eagerly, other requests will assume it's injected and try to get the backdoor. They might fail to get the backdoor and increment triedCount, eventually giving up. Or if they fail, they will see injected=true and just skip attempting injection again.

# Let's add @Volatile private var isInjecting = false
# Wait, I can just use a volatile boolean isInjecting inside the object.

target = """    @Volatile private var injected = false

    // Cached state to avoid file I/O on every transaction"""

replacement = """    @Volatile private var injected = false
    @Volatile private var isInjecting = false

    // Cached state to avoid file I/O on every transaction"""

if target in content:
    content = content.replace(target, replacement)

target2 = """            if (!injected) {
                Logger.i("DRM: Backdoor not found, attempting injection into DRM process...")"""

replacement2 = """            if (!injected && !isInjecting) {
                isInjecting = true
                Logger.i("DRM: Backdoor not found, attempting injection into DRM process...")"""

if target2 in content:
    content = content.replace(target2, replacement2)

target3 = """                Thread {
                    val p = Runtime.getRuntime().exec(
                        arrayOf(
                            "$modulePath/inject",
                            pid.toString(),
                            "$modulePath/libcleverestricky.so",
                            "entry"
                        )
                    )
                    try {
                        p.inputStream.readBytes()
                    } catch (_: Exception) {}
                    finally {
                        try { p.errorStream.readBytes() } catch (_: Exception) {}
                    }
                    val exitCode = p.waitFor()
                    if (exitCode != 0) {
                        Logger.e("DRM: Injection failed (exit=$exitCode)")
                    } else {
                        Logger.i("DRM: Injection succeeded for PID=$pid")
                        injected = true
                    }
                }.start()"""

replacement3 = """                Thread {
                    try {
                        val p = Runtime.getRuntime().exec(
                            arrayOf(
                                "$modulePath/inject",
                                pid.toString(),
                                "$modulePath/libcleverestricky.so",
                                "entry"
                            )
                        )
                        try {
                            p.inputStream.readBytes()
                        } catch (_: Exception) {}
                        finally {
                            try { p.errorStream.readBytes() } catch (_: Exception) {}
                        }
                        val exitCode = p.waitFor()
                        if (exitCode != 0) {
                            Logger.e("DRM: Injection failed (exit=$exitCode)")
                        } else {
                            Logger.i("DRM: Injection succeeded for PID=$pid")
                            injected = true
                        }
                    } finally {
                        isInjecting = false
                    }
                }.start()"""

if target3 in content:
    content = content.replace(target3, replacement3)

with open(file_path, 'w') as f:
    f.write(content)

print("Patch applied successfully.")
