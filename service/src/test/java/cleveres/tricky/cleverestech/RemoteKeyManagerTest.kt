package cleveres.tricky.cleverestech

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64

class RemoteKeyManagerTest {

    @Before
    fun setup() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) { println("D/$tag: $msg") }
            override fun e(tag: String, msg: String) { println("E/$tag: $msg") }
            override fun e(tag: String, msg: String, t: Throwable?) { println("E/$tag: $msg"); t?.printStackTrace() }
            override fun i(tag: String, msg: String) { println("I/$tag: $msg") }
        })
    }

    @Test
    fun testParseAndRotation() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val kp1 = kpg.generateKeyPair()
        val kp2 = kpg.generateKeyPair()

        val pem1 = toPem(kp1)
        val pem2 = toPem(kp2)

        val xml = """
            <RemoteKeyProvisioning>
                <Keys>
                    <Key>
                        <PrivateKey format="pem">
$pem1
                        </PrivateKey>
                        <PublicKeyCose>SGVsbG8=</PublicKeyCose>
                    </Key>
                    <Key>
                        <PrivateKey format="pem">
$pem2
                        </PrivateKey>
                    </Key>
                </Keys>
                <HardwareInfo>
                    <VersionNumber>3</VersionNumber>
                    <RpcAuthorName>TestAuth</RpcAuthorName>
                </HardwareInfo>
            </RemoteKeyProvisioning>
        """.trimIndent()

        val tempFile = File.createTempFile("remote_keys", ".xml")
        tempFile.writeText(xml)

        RemoteKeyManager.update(tempFile)

        val hwInfo = RemoteKeyManager.getHardwareInfo()
        assertNotNull(hwInfo)
        assertEquals("TestAuth", hwInfo?.rpcAuthorName)
        assertEquals(3, hwInfo?.versionNumber)

        val seenKeys = mutableSetOf<java.security.PublicKey>()
        // Try enough times to likely see both
        for (i in 0 until 50) {
            val key = RemoteKeyManager.getKeyPair()
            assertNotNull(key)
            seenKeys.add(key!!.keyPair.public)
        }

        assertTrue("Should have seen both keys", seenKeys.size == 2)

        tempFile.delete()
    }

    private fun toPem(kp: java.security.KeyPair): String {
        // Convert PKCS#8 to SEC1 with Public Key included
        val privInfo = org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(kp.private.encoded)
        val pubInfo = org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(kp.public.encoded)

        // Extract components
        val algId = privInfo.privateKeyAlgorithm
        val params = algId.parameters
        val priv = kp.private as java.security.interfaces.ECPrivateKey
        val pubBits = pubInfo.publicKeyData

        // Construct SEC1 ECPrivateKey structure
        // ECPrivateKey ::= SEQUENCE {
        //   version INTEGER { ecPrivkeyVer1(1) } (ecPrivkeyVer1),
        //   privateKey OCTET STRING,
        //   parameters [0] ECParameters OPTIONAL,
        //   publicKey [1] BIT STRING OPTIONAL }

        val vec = org.bouncycastle.asn1.ASN1EncodableVector()
        vec.add(org.bouncycastle.asn1.ASN1Integer(1)) // version
        vec.add(org.bouncycastle.asn1.DEROctetString(priv.s.toByteArray().let {
            // BigInteger.toByteArray() might have leading zero, strip it if needed?
            // Fixed length? OctetString usually fits content.
            // BouncyCastle BigIntegers.asUnsignedByteArray() is safer.
            // But here we rely on basic java.
            // Let's use simple BigInteger.toByteArray() but ensure positive?
            // priv.s is positive.
             // Remove leading zero if present and length > 32 (for P-256)
             val bytes = it
             if (bytes.size > 32 && bytes[0] == 0.toByte()) {
                 java.util.Arrays.copyOfRange(bytes, 1, bytes.size)
             } else {
                 bytes
             }
        }))

        // parameters [0]
        vec.add(org.bouncycastle.asn1.DERTaggedObject(true, 0, params))
        // publicKey [1]
        vec.add(org.bouncycastle.asn1.DERTaggedObject(true, 1, pubBits))

        val sec1 = org.bouncycastle.asn1.DERSequence(vec)
        val encoded = sec1.encoded

        val sb = StringBuilder()
        sb.append("-----BEGIN EC PRIVATE KEY-----\n")
        sb.append(Base64.getMimeEncoder().encodeToString(encoded))
        sb.append("\n-----END EC PRIVATE KEY-----")
        return sb.toString()
    }
}
