package cleveres.tricky.encryptor

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.Signature

/** Android-only key adapter. Portable CBOX/KDF/AEAD/storage logic lives in Rust. */
internal object MobileCrypto {
    // Preserve the established alias and RSA public-key identity across app upgrades.
    private const val KEY_ALIAS = "cleveres_encryptor_signing_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    private const val MAX_AUTHOR_UTF16_UNITS = 1024
    private const val MAX_AUTHOR_UTF8_BYTES = 4 * MAX_AUTHOR_UTF16_UNITS
    private const val MAX_XML_BYTES = 10 * 1024 * 1024
    private const val MIN_PASSWORD_UTF16_UNITS = 12
    private const val MAX_PASSWORD_UTF16_UNITS = 1024

    internal enum class EncryptResult {
        SUCCESS,
        INVALID_INPUT,
        SIGNING_FAILURE,
        NATIVE_FAILURE,
    }

    fun ensureSigningKey() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateKey(strongBox = true)
                return
            } catch (_: ProviderException) {
                // StrongBox is optional. Fall back to the platform-backed Android Keystore.
            }
        }
        generateKey(strongBox = false)
    }

    fun publicKeyBase64(): String? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    fun encryptAndSave(
        noBackupDirectory: String,
        filename: String,
        author: String,
        xmlUtf8: ByteArray,
        password: String,
    ): EncryptResult {
        if (
            author.isBlank() ||
            author.length > MAX_AUTHOR_UTF16_UNITS ||
            xmlUtf8.isEmpty() ||
            xmlUtf8.size > MAX_XML_BYTES ||
            password.length !in MIN_PASSWORD_UTF16_UNITS..MAX_PASSWORD_UTF16_UNITS
        ) {
            return EncryptResult.INVALID_INPUT
        }

        val authorUtf8 = author.toByteArray(Charsets.UTF_8)
        if (authorUtf8.size > MAX_AUTHOR_UTF8_BYTES) {
            authorUtf8.fill(0)
            return EncryptResult.INVALID_INPUT
        }
        val passwordUtf16 = password.toCharArray()
        var signatureBytes: ByteArray? = null
        var signatureBase64: ByteArray? = null
        try {
            ensureSigningKey()
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry =
                keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                    ?: return EncryptResult.SIGNING_FAILURE

            val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
            signer.initSign(entry.privateKey)
            CboxSignatureV2.update(authorUtf8, xmlUtf8, signer::update)
            signatureBytes = signer.sign()
            signatureBase64 = Base64.encode(signatureBytes, Base64.NO_WRAP)

            return if (
                NativeCrypto.encryptAndSave(
                    noBackupDirectory,
                    filename,
                    authorUtf8,
                    xmlUtf8,
                    signatureBase64,
                    passwordUtf16,
                )
            ) {
                EncryptResult.SUCCESS
            } else {
                EncryptResult.NATIVE_FAILURE
            }
        } catch (_: ProviderException) {
            return EncryptResult.SIGNING_FAILURE
        } catch (_: GeneralSecurityException) {
            return EncryptResult.SIGNING_FAILURE
        } catch (_: IOException) {
            return EncryptResult.SIGNING_FAILURE
        } catch (_: IllegalStateException) {
            return EncryptResult.NATIVE_FAILURE
        } finally {
            authorUtf8.fill(0)
            passwordUtf16.fill('\u0000')
            signatureBytes?.fill(0)
            signatureBase64?.fill(0)
        }
    }

    private fun generateKey(strongBox: Boolean) {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
        val builder =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setKeySize(3072)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setUserAuthenticationRequired(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
            if (strongBox) builder.setIsStrongBoxBacked(true)
        }
        generator.initialize(builder.build())
        generator.generateKeyPair()
    }
}
