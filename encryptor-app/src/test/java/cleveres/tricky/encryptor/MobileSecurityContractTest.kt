package cleveres.tricky.encryptor

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileSecurityContractTest {
    @Test
    fun `mobile app keeps the same hardened trust boundaries as native services`() {
        val root = locateRoot()
        val manifest = File(root, "encryptor-app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:name=\".SecureMainActivity\""))
        assertFalse(manifest.contains("android:name=\".MainActivity\""))
        assertFalse(manifest.contains("android.permission.INTERNET"))

        for (path in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val rules = File(root, "encryptor-app/src/main/res/xml/$path").readText()
            assertFalse("Backup rules must never include app data", rules.contains("<include"))
            assertTrue("Backup rules must explicitly exclude data", rules.contains("<exclude"))
        }

        val activity = File(root, "encryptor-app/src/main/java/cleveres/tricky/encryptor/SecureMainActivity.kt").readText()
        assertTrue(activity.contains("WindowManager.LayoutParams.FLAG_SECURE"))
        assertTrue(activity.contains("context.noBackupFilesDir"))
        assertFalse(activity.contains("getExternalFilesDir"))

        val mobileCrypto = File(root, "encryptor-app/src/main/java/cleveres/tricky/encryptor/MobileCrypto.kt").readText()
        assertTrue(mobileCrypto.contains("AndroidKeyStore"))
        assertTrue(mobileCrypto.contains("setIsStrongBoxBacked(true)"))
        assertTrue(mobileCrypto.contains("NativeCrypto.encryptAndSave"))

        val native = File(root, "rust/encryptor-native/src/lib.rs").readText()
        assertTrue(native.contains("#![forbid(unsafe_code)]"))
        assertTrue(native.contains("TrustedDir::open"))
        assertTrue(native.contains("atomic_write"))
        assertTrue(native.contains("0o700"))
        assertTrue(native.contains("0o600"))
        assertTrue(native.contains("panic::catch_unwind"))

        assertFalse(File(root, "encryptor-app/src/main/java/cleveres/tricky/encryptor/CryptoUtils.kt").exists())
        assertFalse(File(root, "encryptor-app/src/main/java/cleveres/tricky/encryptor/MainActivity.kt").exists())
    }

    @Test
    fun `mobile and module versions come from the same root source`() {
        val root = locateRoot()
        val build = File(root, "encryptor-app/build.gradle.kts").readText()
        assertTrue(build.contains("versionCode = moduleVersionCode"))
        assertTrue(build.contains("versionName = moduleVersionName"))
        assertTrue(build.contains("rootProject.extra[\"verCode\"]"))
        assertTrue(build.contains("rootProject.extra[\"verName\"]"))
        assertFalse(build.contains("versionCode = 1"))
        assertFalse(build.contains("versionName = \"1.0\""))
    }

    private fun locateRoot(): File {
        var current = File(System.getProperty("user.dir")).canonicalFile
        repeat(5) {
            if (File(current, "encryptor-app").isDirectory && File(current, "rust").isDirectory) {
                return current
            }
            current = current.parentFile ?: return@repeat
        }
        error("Repository root not found")
    }
}
