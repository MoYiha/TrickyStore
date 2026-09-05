package cleveres.tricky.cleverestech

import android.os.Parcel
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.keystore2.IKeystoreSecurityLevel
import android.system.keystore2.KeyDescriptor
import android.system.keystore2.KeyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import cleveres.tricky.cleverestech.keystore.Utils
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.security.auth.x500.X500Principal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttestationKeyContractTest {
    @Test
    fun `real AIDL parcels preserve issuer selection and cursor`() {
        for (explicitIssuer in listOf(false, true)) {
            val request = Parcel.obtain()
            try {
                request.writeInt(42)
                val start = request.dataPosition()
                request.writeInterfaceToken(IKeystoreSecurityLevel.DESCRIPTOR)
                request.writeTypedObject(KeyDescriptor().apply { alias = "child" }, 0)
                request.writeTypedObject(
                    if (explicitIssuer) KeyDescriptor().apply { alias = "issuer" } else null,
                    0,
                )
                request.setDataPosition(start)
                assertEquals(!explicitIssuer, Utils.usesDefaultAttestationKey(request))
                assertEquals(start, request.dataPosition())
                assertEquals(!explicitIssuer, Utils.usesDefaultAttestationKey(request))
            } finally {
                request.recycle()
            }
        }

        val truncated = Parcel.obtain()
        try {
            truncated.writeInterfaceToken(IKeystoreSecurityLevel.DESCRIPTOR)
            truncated.writeTypedObject(KeyDescriptor().apply { alias = "child" }, 0)
            truncated.setDataPosition(0)
            assertFalse(Utils.usesDefaultAttestationKey(truncated))
            assertEquals(0, truncated.dataPosition())
        } finally {
            truncated.recycle()
        }
    }

    @Test
    fun `Android Keystore caller signed graph returns leaf only with verifiable edges`() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val prefix = "ct-attest-contract-${UUID.randomUUID()}"
        val aliases = listOf("$prefix-A", "$prefix-B", "$prefix-C", "$prefix-ordinary")
        try {
            generate(aliases[0], KeyProperties.PURPOSE_ATTEST_KEY, null)
            generate(aliases[1], KeyProperties.PURPOSE_ATTEST_KEY, aliases[0])
            generate(aliases[2], KeyProperties.PURPOSE_SIGN, aliases[1])
            generate(aliases[3], KeyProperties.PURPOSE_SIGN, null)

            for ((issuerAlias, childAlias) in listOf(aliases[0] to aliases[1], aliases[1] to aliases[2])) {
                val issuer = store.getCertificate(issuerAlias)
                val child = store.getCertificate(childAlias)
                child.verify(issuer.publicKey)
                assertEquals(1, store.getCertificateChain(childAlias).size)
                val metadata = KeyMetadata().apply { certificate = child.encoded }
                assertFalse(Utils.isCertificateChainRewriteCandidate(metadata))
                repeat(8) {
                    assertArrayEquals(child.encoded, store.getCertificate(childAlias).encoded)
                    store.getCertificate(childAlias).verify(issuer.publicKey)
                }
            }
            val ordinary = store.getCertificate(aliases[3])
            assertFalse(Utils.hasAndroidAttestationExtension(ordinary))
            val ordinaryMetadata = KeyMetadata().apply { certificate = ordinary.encoded }
            assertFalse(Utils.isCertificateChainRewriteCandidate(ordinaryMetadata))
            ordinaryMetadata.certificateChain = store.getCertificate(aliases[0]).encoded
            assertTrue(Utils.isCertificateChainRewriteCandidate(ordinaryMetadata))
        } finally {
            aliases.reversed().forEach(store::deleteEntry)
        }
    }

    private fun generate(
        alias: String,
        purpose: Int,
        issuer: String?,
    ) {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, purpose)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(X500Principal("CN=$alias"))
        if (issuer != null) {
            spec.setAttestationChallenge(alias.toByteArray(Charsets.UTF_8)).setAttestKeyAlias(issuer)
        }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(spec.build())
        }.generateKeyPair()
    }
}
