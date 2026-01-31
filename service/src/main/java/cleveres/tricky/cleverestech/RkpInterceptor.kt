package cleveres.tricky.cleverestech

import android.hardware.security.rkp.DeviceInfo
import android.hardware.security.rkp.IRemotelyProvisionedComponent
import android.hardware.security.rkp.MacedPublicKey
import android.hardware.security.rkp.ProtectedData
import android.hardware.security.rkp.RpcHardwareInfo
import android.os.IBinder
import android.os.Parcel
import cleveres.tricky.cleverestech.binder.BinderInterceptor
import cleveres.tricky.cleverestech.keystore.CertHack
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Intercepts IRemotelyProvisionedComponent transactions to spoof RKP responses.
 * Primary goal: Achieve MEETS_STRONG_INTEGRITY in Play Integrity.
 * 
 * Supports both legacy (V1) and modern (V2) certificate request flows.
 */
class RkpInterceptor(
    private val original: IRemotelyProvisionedComponent,
    private val securityLevel: Int
) : BinderInterceptor() {

    companion object {
        private val getHardwareInfoTransaction = 
            getTransactCode(IRemotelyProvisionedComponent.Stub::class.java, "getHardwareInfo")
        private val generateEcdsaP256KeyPairTransaction = 
            getTransactCode(IRemotelyProvisionedComponent.Stub::class.java, "generateEcdsaP256KeyPair")
        private val generateCertificateRequestTransaction = 
            getTransactCode(IRemotelyProvisionedComponent.Stub::class.java, "generateCertificateRequest")
        private val generateCertificateRequestV2Transaction = 
            getTransactCode(IRemotelyProvisionedComponent.Stub::class.java, "generateCertificateRequestV2")
        
        // Cache for generated key pairs
        private val keyPairCache = KeyCache<Int, KeyPairInfo>(100)
        private var keyPairCounter = 0
        
        data class KeyPairInfo(
            val keyPair: KeyPair,
            val macedPublicKey: ByteArray,
            val privateKeyHandle: ByteArray
        )
    }

    override fun onPreTransact(
        target: IBinder,
        code: Int,
        flags: Int,
        callingUid: Int,
        callingPid: Int,
        data: Parcel
    ): Result {
        if (!Config.shouldBypassRkp()) return Skip
        
        when (code) {
            getHardwareInfoTransaction -> {
                Logger.i("intercepting RKP getHardwareInfo for uid=$callingUid")
                return interceptGetHardwareInfo()
            }
            generateEcdsaP256KeyPairTransaction -> {
                Logger.i("intercepting RKP generateEcdsaP256KeyPair for uid=$callingUid")
                return interceptKeyPairGeneration(callingUid, data)
            }
            generateCertificateRequestTransaction -> {
                Logger.i("intercepting RKP generateCertificateRequest for uid=$callingUid")
                return interceptCertificateRequest(callingUid, data, false)
            }
            generateCertificateRequestV2Transaction -> {
                Logger.i("intercepting RKP generateCertificateRequestV2 for uid=$callingUid")
                return interceptCertificateRequest(callingUid, data, true)
            }
        }
        return Skip
    }

    override fun onPostTransact(
        target: IBinder,
        code: Int,
        flags: Int,
        callingUid: Int,
        callingPid: Int,
        data: Parcel,
        reply: Parcel?,
        resultCode: Int
    ): Result {
        // Post-transaction handling if needed
        return Skip
    }

    /**
     * Intercepts getHardwareInfo to return spoofed RPC hardware info.
     */
    private fun interceptGetHardwareInfo(): Result {
        kotlin.runCatching {
            val info = RpcHardwareInfo().apply {
                versionNumber = 3 // Version 3 for Android 14+
                rpcAuthorName = "Google"
                supportedEekCurve = 2 // CURVE_P256
                uniqueId = Config.getBuildVar("DEVICE") ?: "generic"
                supportedNumKeysInCsr = 20
            }
            
            val p = Parcel.obtain()
            p.writeNoException()
            p.writeTypedObject(info, 0)
            return OverrideReply(0, p)
        }.onFailure {
            Logger.e("failed to intercept getHardwareInfo", it)
        }
        return Skip
    }

    /**
     * Intercepts key pair generation and returns spoofed EC P-256 key pair.
     */
    private fun interceptKeyPairGeneration(uid: Int, data: Parcel): Result {
        kotlin.runCatching {
            data.enforceInterface(IRemotelyProvisionedComponent.DESCRIPTOR)
            val testMode = data.readInt() != 0
            
            // Generate EC P-256 key pair
            val keyPairGen = KeyPairGenerator.getInstance("EC")
            keyPairGen.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = keyPairGen.generateKeyPair()
            
            // Create COSE_Mac0 wrapped public key
            val macedKey = createMacedPublicKey(keyPair)
            
            // Create private key handle (just an index for our cache)
            val handleIndex = keyPairCounter++
            val privateKeyHandle = ByteArray(32)
            privateKeyHandle[0] = (handleIndex shr 24).toByte()
            privateKeyHandle[1] = (handleIndex shr 16).toByte()
            privateKeyHandle[2] = (handleIndex shr 8).toByte()
            privateKeyHandle[3] = handleIndex.toByte()
            
            // Cache the key pair info
            keyPairCache[handleIndex] = KeyPairInfo(keyPair, macedKey, privateKeyHandle)
            
            Logger.i("generated RKP key pair handle=$handleIndex for uid=$uid")
            
            val p = Parcel.obtain()
            p.writeNoException()
            // Write MacedPublicKey
            val mpk = MacedPublicKey(macedKey)
            p.writeTypedObject(mpk, 0)
            // Write private key handle as out parameter
            p.writeByteArray(privateKeyHandle)
            
            return OverrideReply(0, p)
        }.onFailure {
            Logger.e("failed to intercept key pair generation for uid=$uid", it)
        }
        return Skip
    }

    /**
     * Intercepts certificate request and returns spoofed response.
     */
    private fun interceptCertificateRequest(uid: Int, data: Parcel, isV2: Boolean): Result {
        kotlin.runCatching {
            data.enforceInterface(IRemotelyProvisionedComponent.DESCRIPTOR)
            
            val keysToSign: Array<MacedPublicKey>?
            val challenge: ByteArray?
            
            if (isV2) {
                // V2: keysToSign, challenge
                keysToSign = data.createTypedArray(MacedPublicKey.CREATOR)
                challenge = data.createByteArray()
            } else {
                // V1: testMode, keysToSign, endpointEncryptionCertChain, challenge, deviceInfo, protectedData
                val testMode = data.readInt() != 0
                keysToSign = data.createTypedArray(MacedPublicKey.CREATOR)
                val endpointCertChain = data.createByteArray()
                challenge = data.createByteArray()
            }
            
            // Create spoofed certificate request response
            val response = createCertificateRequestResponse(keysToSign, challenge, isV2)
            
            Logger.i("generated RKP certificate request response for uid=$uid isV2=$isV2")
            
            val p = Parcel.obtain()
            p.writeNoException()
            
            if (isV2) {
                // V2 returns byte[] directly
                p.writeByteArray(response)
            } else {
                // V1 also populates DeviceInfo and ProtectedData out params
                p.writeByteArray(response)
                // Write DeviceInfo
                val deviceInfo = DeviceInfo(createDeviceInfo())
                p.writeTypedObject(deviceInfo, 0)
                // Write ProtectedData
                val protectedData = ProtectedData(createProtectedData())
                p.writeTypedObject(protectedData, 0)
            }
            
            return OverrideReply(0, p)
        }.onFailure {
            Logger.e("failed to intercept certificate request for uid=$uid", it)
        }
        return Skip
    }

    /**
     * Creates a COSE_Mac0 wrapped public key.
     */
    private fun createMacedPublicKey(keyPair: KeyPair): ByteArray {
        // Simplified COSE_Mac0 structure for EC P-256 public key
        // In production, this should use proper COSE encoding
        val pubKey = keyPair.public.encoded
        
        // COSE_Mac0 = [protected, unprotected, payload, tag]
        // For simplicity, we create a basic structure
        // TODO: Use proper CBOR/COSE library for production
        return CertHack.generateMacedPublicKey(keyPair) ?: pubKey
    }

    /**
     * Creates a spoofed certificate request response.
     */
    private fun createCertificateRequestResponse(
        keysToSign: Array<MacedPublicKey>?,
        challenge: ByteArray?,
        isV2: Boolean
    ): ByteArray {
        // Create CBOR encoded certificate request
        // This should contain the public keys and challenge signed appropriately
        return CertHack.createCertificateRequestResponse(
            keysToSign?.map { it.macedKey }?.filterNotNull() ?: emptyList(),
            challenge ?: ByteArray(0),
            createDeviceInfo()
        ) ?: ByteArray(0)
    }

    /**
     * Creates CBOR encoded device info.
     */
    private fun createDeviceInfo(): ByteArray {
        // CBOR map with device information
        // Keys: brand, manufacturer, product, model, device, vb_state, etc.
        val brand = Config.getBuildVar("BRAND") ?: "google"
        val manufacturer = Config.getBuildVar("MANUFACTURER") ?: "Google"
        val product = Config.getBuildVar("PRODUCT") ?: "generic"
        val model = Config.getBuildVar("MODEL") ?: "Pixel"
        val device = Config.getBuildVar("DEVICE") ?: "generic"
        
        // Simplified device info - should use proper CBOR encoding
        // TODO: Use proper CBOR library
        return CertHack.createDeviceInfoCbor(brand, manufacturer, product, model, device)
            ?: ByteArray(0)
    }

    /**
     * Creates COSE_Encrypt protected data.
     */
    private fun createProtectedData(): ByteArray {
        // COSE_Encrypt structure containing MAC key
        // For now, return empty - this is populated if endpoint encryption is needed
        return ByteArray(0)
    }
}
