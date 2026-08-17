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
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
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
        assertTrue(mobileCrypto.contains("setKeySize(3072)"))
        assertTrue(mobileCrypto.contains("setIsStrongBoxBacked(true)"))
        assertTrue(mobileCrypto.contains("NativeCrypto.encryptAndSave"))
        assertFalse(mobileCrypto.contains("PBKDF2"))
        assertFalse(mobileCrypto.contains("AES/GCM"))

        val vault = File(root, "encryptor-app/src/main/java/cleveres/tricky/encryptor/VaultStore.kt").readText()
        assertTrue(vault.contains("NativeCrypto.ensureVault"))
        assertTrue(vault.contains("NativeCrypto.readEncrypted"))
        assertTrue(vault.contains("NativeCrypto.deleteEncrypted"))
        assertTrue(vault.contains("NativeCrypto.storeEncrypted"))

        val native = File(root, "rust/encryptor-native/src/lib.rs").readText()
        assertTrue(native.contains("#![forbid(unsafe_code)]"))
        assertTrue(native.contains("parse_keybox_xml_bytes"))
        assertTrue(native.contains("TrustedDir::open"))
        assertTrue(native.contains("atomic_write"))
        assertTrue(native.contains("read_bounded"))
        assertTrue(native.contains("unlink_file"))
        assertTrue(native.contains("0o700"))
        assertTrue(native.contains("0o600"))
        assertTrue(native.contains("panic::catch_unwind"))

        assertFalse(File(root, "encryptor-app/src/main/java/cleveres/tricky/encryptor/CryptoUtils.kt").exists())
        assertFalse(File(root, "encryptor-app/src/main/java/cleveres/tricky/encryptor/MainActivity.kt").exists())
    }

    @Test
    fun `mobile and module versions come from the same 2_6_0 root source`() {
        val root = locateRoot()
        val rootBuild = File(root, "build.gradle.kts").readText()
        val appBuild = File(root, "encryptor-app/build.gradle.kts").readText()
        assertTrue(rootBuild.contains("val verName = \"V2.6.0\""))
        assertTrue(appBuild.contains("versionCode = moduleVersionCode"))
        assertTrue(appBuild.contains("versionName = moduleVersionName"))
        assertTrue(appBuild.contains("rootProject.extra[\"verCode\"]"))
        assertTrue(appBuild.contains("rootProject.extra[\"verName\"]"))
        assertFalse(appBuild.contains("versionCode = 1"))
        assertFalse(appBuild.contains("versionName = \"1.0\""))
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
