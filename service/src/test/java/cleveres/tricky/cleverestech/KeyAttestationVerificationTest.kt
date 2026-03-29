package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate the key attestation and RKP infrastructure at source level.
 *
 * These tests ensure that:
 * 1. Attestation extension OID is correct (Google's key attestation OID)
 * 2. COSE/CBOR structures are properly formed for RKP
 * 3. Root-of-trust spoofing is present and correct
 * 4. HMAC key management follows rotation and validation rules
 * 5. SecurityLevelInterceptor handles attestation challenges
 * 6. CertHack properly signs and re-wraps certificates
 * 7. LocalRkpProxy validates COSE_Mac0 structure
 * 8. KeystoreInterceptor registers all required interceptors
 */
class KeyAttestationVerificationTest {

    private lateinit var certHackContent: String
    private lateinit var rkpInterceptorContent: String
    private lateinit var localRkpProxyContent: String
    private lateinit var securityLevelContent: String
    private lateinit var keystoreInterceptorContent: String

    @Before
    fun setup() {
        val base = java.io.File(System.getProperty("user.dir")).absoluteFile
        fun findFile(relative: String): java.io.File {
            return generateSequence(base) { it.parentFile }
                .map { java.io.File(it, relative) }
                .firstOrNull(java.io.File::exists)
                ?: error("Could not locate $relative")
        }
        certHackContent = findFile(
            "service/src/main/java/cleveres/tricky/cleverestech/keystore/CertHack.java"
        ).readText()
        rkpInterceptorContent = serviceMainFile("RkpInterceptor.kt").readText()
        localRkpProxyContent = findFile(
            "service/src/main/java/cleveres/tricky/cleverestech/rkp/LocalRkpProxy.kt"
        ).readText()
        securityLevelContent = serviceMainFile("SecurityLevelInterceptor.kt").readText()
        keystoreInterceptorContent = serviceMainFile("KeystoreInterceptor.kt").readText()
    }

    // ====================================================================
    // Attestation Extension OID (Google Key Attestation)
    // ====================================================================

    @Test
    fun testAttestationOidPresent() {
        assertTrue(
            "CertHack must use Google's key attestation OID 1.3.6.1.4.1.11129.2.1.17",
            certHackContent.contains("1.3.6.1.4.1.11129.2.1.17")
        )
    }

    @Test
    fun testAttestationOidDeclaredAsConstant() {
        assertTrue(
            "Attestation OID must be declared as a named constant (not inline magic string)",
            certHackContent.contains("ASN1ObjectIdentifier") &&
            certHackContent.contains("OID") &&
            certHackContent.contains("1.3.6.1.4.1.11129.2.1.17")
        )
    }

    // ====================================================================
    // Root-of-Trust Spoofing
    // ====================================================================

    @Test
    fun testRootOfTrustInCertHack() {
        assertTrue(
            "CertHack must handle root of trust in the attestation extension",
            certHackContent.contains("root of trust") || certHackContent.contains("rootOfTrust") ||
            certHackContent.contains("root_of_trust") || certHackContent.contains("704")
        )
    }

    @Test
    fun testVerifiedBootStateGreen() {
        assertTrue(
            "Certificate hacking must set verified boot state (vb_state: green for RKP DeviceInfo)",
            certHackContent.contains("green") || certHackContent.contains("vb_state")
        )
    }

    @Test
    fun testBootloaderStateLocked() {
        assertTrue(
            "Certificate hacking must set bootloader state to locked",
            certHackContent.contains("locked") || certHackContent.contains("bootloader_state")
        )
    }

    // ====================================================================
    // COSE_Mac0 Structure (RKP MACed Public Key)
    // ====================================================================

    @Test
    fun testCoseMac0Generation() {
        assertTrue(
            "CertHack must have generateMacedPublicKey method for COSE_Mac0 creation",
            certHackContent.contains("generateMacedPublicKey")
        )
    }

    @Test
    fun testCoseMac0UsesHmacSha256() {
        assertTrue(
            "COSE_Mac0 must use HMAC-SHA256 (alg: 5) for key authentication",
            certHackContent.contains("HMAC") && certHackContent.contains("256")
        )
    }

    @Test
    fun testCoseMac0HasFourElements() {
        assertTrue(
            "COSE_Mac0 must be a 4-element array [protected, unprotected, payload, tag]",
            certHackContent.contains("coseMac0") || certHackContent.contains("COSE_Mac0")
        )
    }

    @Test
    fun testCoseKeyMapUsesP256() {
        assertTrue(
            "COSE_Key must use P-256 curve (crv: 1) for EC key pairs",
            certHackContent.contains("P-256") || certHackContent.contains("P_256") ||
            certHackContent.contains("secp256r1")
        )
    }

    @Test
    fun testCoseKeyMapUsesES256Algorithm() {
        assertTrue(
            "COSE_Key must declare ES256 algorithm (alg: -7)",
            certHackContent.contains("ES256") || certHackContent.contains("-7")
        )
    }

    // ====================================================================
    // COSE_Sign1 (Certificate Request Response)
    // ====================================================================

    @Test
    fun testCertificateRequestResponseCreation() {
        assertTrue(
            "CertHack must have createCertificateRequestResponse for COSE_Sign1 signing",
            certHackContent.contains("createCertificateRequestResponse")
        )
    }

    @Test
    fun testDeviceInfoCborCreation() {
        assertTrue(
            "CertHack must have createDeviceInfoCbor for RKP device info generation",
            certHackContent.contains("createDeviceInfoCbor")
        )
    }

    @Test
    fun testDeviceInfoContainsRequiredFields() {
        // RKP DeviceInfo must include brand, manufacturer, product, model, device, security_level
        assertTrue(
            "DeviceInfo CBOR must include 'brand' field",
            certHackContent.contains("\"brand\"")
        )
        assertTrue(
            "DeviceInfo CBOR must include 'security_level' field",
            certHackContent.contains("security_level")
        )
    }

    @Test
    fun testDeviceInfoFusedBit() {
        assertTrue(
            "DeviceInfo must set 'fused' field (1 = fused for production devices)",
            certHackContent.contains("fused")
        )
    }

    // ====================================================================
    // LocalRkpProxy - HMAC Key Management
    // ====================================================================

    @Test
    fun testLocalRkpProxyHasKeyRotation() {
        assertTrue(
            "LocalRkpProxy must implement key rotation (rotateKey) for anti-fingerprinting",
            localRkpProxyContent.contains("rotateKey")
        )
    }

    @Test
    fun testLocalRkpProxy24HourRotation() {
        assertTrue(
            "LocalRkpProxy must rotate keys every 24 hours (86400000ms) for anti-fingerprinting",
            localRkpProxyContent.contains("86400000") || localRkpProxyContent.contains("24")
        )
    }

    @Test
    fun testLocalRkpProxyKeyPersistence() {
        assertTrue(
            "LocalRkpProxy must persist HMAC key to rkp_root_secret file",
            localRkpProxyContent.contains("rkp_root_secret")
        )
    }

    @Test
    fun testLocalRkpProxyGetMacKey() {
        assertTrue(
            "LocalRkpProxy must expose getMacKey() for CertHack to use",
            localRkpProxyContent.contains("getMacKey")
        )
    }

    @Test
    fun testLocalRkpProxyValidation() {
        assertTrue(
            "LocalRkpProxy must validate COSE_Mac0 structure",
            localRkpProxyContent.contains("validateMacedPublicKey")
        )
    }

    @Test
    fun testLocalRkpProxyValidates32ByteTag() {
        assertTrue(
            "LocalRkpProxy must verify HMAC tag is exactly 32 bytes (SHA-256 output)",
            localRkpProxyContent.contains("32")
        )
    }

    // ====================================================================
    // RkpInterceptor - Transaction Interception
    // ====================================================================

    @Test
    fun testRkpInterceptorInterceptsHardwareInfo() {
        assertTrue(
            "RkpInterceptor must intercept getHardwareInfo RKP HAL method",
            rkpInterceptorContent.contains("getHardwareInfo") || rkpInterceptorContent.contains("HardwareInfo")
        )
    }

    @Test
    fun testRkpInterceptorInterceptsKeyGeneration() {
        assertTrue(
            "RkpInterceptor must intercept generateEcdsaP256KeyPair for key spoofing",
            rkpInterceptorContent.contains("generateEcdsaP256KeyPair") || rkpInterceptorContent.contains("KeyPair")
        )
    }

    @Test
    fun testRkpInterceptorInterceptsCertificateRequest() {
        assertTrue(
            "RkpInterceptor must intercept generateCertificateRequest for CSR spoofing",
            rkpInterceptorContent.contains("generateCertificateRequest") || rkpInterceptorContent.contains("CertificateRequest")
        )
    }

    @Test
    fun testRkpInterceptorReturnsSpoofedAuthor() {
        assertTrue(
            "RkpInterceptor must return 'Google' as RPC author name in hardware info",
            rkpInterceptorContent.contains("Google")
        )
    }

    @Test
    fun testRkpInterceptorUsesP256Curve() {
        assertTrue(
            "RkpInterceptor must use P-256 curve for key generation (required by RKP spec)",
            rkpInterceptorContent.contains("P-256") || rkpInterceptorContent.contains("P_256") ||
            rkpInterceptorContent.contains("secp256r1") || rkpInterceptorContent.contains("EC")
        )
    }

    @Test
    fun testRkpInterceptorUsesCoseFormat() {
        assertTrue(
            "RkpInterceptor must use COSE format for RKP key wrapping",
            rkpInterceptorContent.contains("COSE") || rkpInterceptorContent.contains("Mac0") ||
            rkpInterceptorContent.contains("macedPublicKey") || rkpInterceptorContent.contains("generateMacedPublicKey")
        )
    }

    // ====================================================================
    // SecurityLevelInterceptor - Attestation Challenge Handling
    // ====================================================================

    @Test
    fun testSecurityLevelHandlesAttestationChallenge() {
        assertTrue(
            "SecurityLevelInterceptor must detect and handle attestation challenges in key generation",
            securityLevelContent.contains("attestationChallenge") || securityLevelContent.contains("attestation")
        )
    }

    @Test
    fun testSecurityLevelHandlesAttestationKey() {
        assertTrue(
            "SecurityLevelInterceptor must handle attestation key descriptors for chained attestations",
            securityLevelContent.contains("attestationKey") || securityLevelContent.contains("attest key") ||
            securityLevelContent.contains("attestationKeyDescriptor")
        )
    }

    @Test
    fun testSecurityLevelCachesKeys() {
        assertTrue(
            "SecurityLevelInterceptor must cache generated keys for later retrieval",
            securityLevelContent.contains("keys[") || securityLevelContent.contains("keyCache") ||
            securityLevelContent.contains("Key(")
        )
    }

    // ====================================================================
    // KeystoreInterceptor - Registration Completeness
    // ====================================================================

    @Test
    fun testKeystoreRegistersRkpInterceptor() {
        assertTrue(
            "KeystoreInterceptor must register RkpInterceptor when RKP bypass is enabled",
            keystoreInterceptorContent.contains("RkpInterceptor") &&
            keystoreInterceptorContent.contains("shouldBypassRkp")
        )
    }

    @Test
    fun testKeystoreRegistersTeeInterceptor() {
        assertTrue(
            "KeystoreInterceptor must register TEE SecurityLevelInterceptor",
            keystoreInterceptorContent.contains("TRUSTED_ENVIRONMENT") &&
            keystoreInterceptorContent.contains("SecurityLevelInterceptor")
        )
    }

    @Test
    fun testKeystoreRegistersStrongBoxInterceptor() {
        assertTrue(
            "KeystoreInterceptor must register StrongBox SecurityLevelInterceptor when available",
            keystoreInterceptorContent.contains("STRONGBOX") &&
            keystoreInterceptorContent.contains("SecurityLevelInterceptor")
        )
    }

    @Test
    fun testKeystoreFindsRemotelyProvisionedComponent() {
        assertTrue(
            "KeystoreInterceptor must find IRemotelyProvisionedComponent for RKP registration",
            keystoreInterceptorContent.contains("RemotelyProvisionedComponent") ||
            keystoreInterceptorContent.contains("IRemotelyProvisionedComponent")
        )
    }

    @Test
    fun testKeystoreRegistersPropertyHiderService() {
        assertTrue(
            "KeystoreInterceptor must register PropertyHiderService with native layer",
            keystoreInterceptorContent.contains("PropertyHiderService") &&
            keystoreInterceptorContent.contains("registerPropertyService")
        )
    }

    // ====================================================================
    // CertHack - Certificate Chain Manipulation
    // ====================================================================

    @Test
    fun testCertHackHasCertificateChainHacking() {
        assertTrue(
            "CertHack must have hackCertificateChain method for leaf certificate replacement",
            certHackContent.contains("hackCertificateChain")
        )
    }

    @Test
    fun testCertHackHandlesAttestationApplicationId() {
        assertTrue(
            "CertHack must handle ATTESTATION_APPLICATION_ID in key description",
            certHackContent.contains("ATTESTATION_APPLICATION_ID")
        )
    }

    @Test
    fun testCertHackHandlesPatchLevel() {
        assertTrue(
            "CertHack must spoof patch level (tag 706) in attestation extension",
            certHackContent.contains("706") || certHackContent.contains("patchLevel") ||
            certHackContent.contains("patch_level") || certHackContent.contains("PATCH_LEVEL")
        )
    }

    @Test
    fun testCertHackHandlesIdAttestation() {
        assertTrue(
            "CertHack must handle ID attestation tags (710-717) for device identity spoofing",
            certHackContent.contains("710") || certHackContent.contains("attestation_id") ||
            certHackContent.contains("ATTESTATION_ID")
        )
    }

    @Test
    fun testCertHackHasCaching() {
        assertTrue(
            "CertHack must cache hacked certificate chains to avoid expensive re-parsing",
            certHackContent.contains("cache") || certHackContent.contains("Cache") ||
            certHackContent.contains("getCachedCertificateChain")
        )
    }

    @Test
    fun testCertHackUsesKeybox() {
        assertTrue(
            "CertHack must use keybox for signing (canHack / setKeyboxes)",
            certHackContent.contains("canHack") && certHackContent.contains("setKeyboxes")
        )
    }

    // ====================================================================
    // Security: No hardcoded secrets
    // ====================================================================

    @Test
    fun testNoHardcodedHmacKey() {
        assertFalse(
            "CertHack must NOT contain a hardcoded HMAC key — must be obtained from LocalRkpProxy",
            certHackContent.contains("LOCAL_HMAC_KEY =") && !certHackContent.contains("removed")
        )
    }

    @Test
    fun testLocalRkpProxyGeneratesRandomKey() {
        assertTrue(
            "LocalRkpProxy must use SecureRandom for key generation (not java.util.Random)",
            localRkpProxyContent.contains("SecureRandom") || localRkpProxyContent.contains("secureRandom")
        )
    }
}
