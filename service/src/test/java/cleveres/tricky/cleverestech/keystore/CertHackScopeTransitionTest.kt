package cleveres.tricky.cleverestech.keystore

import cleveres.tricky.cleverestech.Config
import cleveres.tricky.cleverestech.CertificateBackend
import cleveres.tricky.cleverestech.ManagedCertificateBackendOracle
import cleveres.tricky.cleverestech.ManagedOpaqueKeyOracle
import cleveres.tricky.cleverestech.MockSecureFileOperations
import cleveres.tricky.cleverestech.PolicyState
import cleveres.tricky.cleverestech.util.SecureFile
import org.bouncycastle.asn1.ASN1Boolean
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Removing an app from identity_target.txt must serve a genuine certificate
 * on the next request: target membership feeds attestation scope, while the
 * certificate cache is keyed by leaf bytes alone.
 */
class CertHackScopeTransitionTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var configDir: File

    @Before
    fun setUp() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        ManagedCertificateBackendOracle.install()
        // Production cache clears report a healthy backend; mirror that so
        // the scope transitions below exercise recomputation, not the
        // unhealthy-graph passthrough path.
        CertificateBackend.clearAttestKeyStoreOverride = { true }
        Config.reset()
        PolicyState.resetForTesting()
        SecureFile.impl = MockSecureFileOperations()
        configDir = tempFolder.newFolder("config")
        Config.setRootForTesting(configDir)
        PolicyState.setRootForTesting(configDir)
    }

    @After
    fun tearDown() {
        ManagedCertificateBackendOracle.reset()
        CertificateBackend.clearAttestKeyStoreOverride = null
        CertHack.resetGraphHealthForTesting()
        Config.reset()
        PolicyState.resetForTesting()
        SecureFile.impl = SecureFile.DefaultSecureFileOperations()
    }

    @Test
    fun `removing identity target serves genuine certificate on next request`() {
        setAttestationIds("BRAND" to "Google".toByteArray(Charsets.UTF_8))
        File(configDir, "spoof_enabled").createNewFile()
        Config.refreshRuntimeSetting("spoof_enabled")
        File(configDir, "identity_target.txt").writeText("com.example.app\n")
        Config.refreshRuntimeSetting("identity_target.txt")
        Config.setPackagesForTesting(13_010, arrayOf("com.example.app"))

        val (keyPair, leaf) = generateIdentityLeaf()
        val previousState = installKeyboxState(keyPair, leaf)
        try {
            val first = CertHack.hackCertificateChain(arrayOf<Certificate>(leaf), 13_010, true)
            assertArrayEquals(
                "Google".toByteArray(Charsets.UTF_8),
                brandOf(first[0] as X509Certificate),
            )

            File(configDir, "identity_target.txt").writeText("com.other.app\n")
            Config.refreshRuntimeSetting("identity_target.txt")

            val second = CertHack.hackCertificateChain(arrayOf<Certificate>(leaf), 13_010, true)
            assertArrayEquals(
                "OriginalBrand".toByteArray(Charsets.UTF_8),
                brandOf(second[0] as X509Certificate),
            )
        } finally {
            restoreKeyboxState(previousState)
            Config.reset()
        }
    }

    private fun setAttestationIds(vararg pairs: Pair<String, ByteArray>) {
        val field = Config::class.java.getDeclaredField("attestationIds")
        field.isAccessible = true
        field.set(Config, pairs.toMap())
    }

    private fun installKeyboxState(keyPair: KeyPair, leaf: X509Certificate): Any? {
        val box = ManagedOpaqueKeyOracle.wrap(keyPair, listOf(leaf), "test.xml")
        val boxes = mapOf("RSA" to listOf(box))
        val files = emptyMap<String, List<CertHack.KeyBox>>()
        val stateClass = Class.forName("cleveres.tricky.cleverestech.keystore.CertHack\$State")
        val ctor = stateClass.getDeclaredConstructor(Map::class.java, Map::class.java)
        ctor.isAccessible = true
        val newState = ctor.newInstance(boxes, files)
        val stateField = CertHack::class.java.getDeclaredField("state")
        stateField.isAccessible = true
        val previousState = stateField.get(null)
        stateField.set(null, newState)
        return previousState
    }

    private fun restoreKeyboxState(previousState: Any?) {
        val stateField = CertHack::class.java.getDeclaredField("state")
        stateField.isAccessible = true
        stateField.set(null, previousState)
    }

    private fun brandOf(certificate: X509Certificate): ByteArray {
        val extBytes = certificate.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")
        val extStruct = ASN1Primitive.fromByteArray(ASN1OctetString.getInstance(extBytes).octets)
        val seq = ASN1Sequence.getInstance(extStruct)
        val teeEnforced = seq.getObjectAt(7) as ASN1Sequence
        for (encodable in teeEnforced) {
            val tagged = encodable as ASN1TaggedObject
            if (tagged.tagNo == 710) {
                return ASN1OctetString.getInstance(tagged.baseObject).octets
            }
        }
        throw AssertionError("BRAND (710) missing")
    }

    private fun validBootDigest(marker: Int): ByteArray = ByteArray(32).also { it[0] = marker.toByte() }

    private fun generateIdentityLeaf(): Pair<KeyPair, X509Certificate> {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val issuer = X500Name("CN=Test")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.ONE,
            Date(),
            Date(System.currentTimeMillis() + 100000),
            issuer,
            keyPair.public,
        )
        val keyDesc = ASN1EncodableVector()
        keyDesc.add(ASN1Integer(100))
        keyDesc.add(ASN1Enumerated(1))
        keyDesc.add(ASN1Integer(100))
        keyDesc.add(ASN1Enumerated(1))
        keyDesc.add(DEROctetString(ByteArray(0)))
        keyDesc.add(DEROctetString(ByteArray(0)))
        keyDesc.add(DERSequence())

        val rootOfTrust = ASN1EncodableVector()
        rootOfTrust.add(DEROctetString(validBootDigest(0x11)))
        rootOfTrust.add(ASN1Boolean.TRUE)
        rootOfTrust.add(ASN1Enumerated(0))
        rootOfTrust.add(DEROctetString(validBootDigest(0x22)))
        val teeEnforced = ASN1EncodableVector()
        teeEnforced.add(DERTaggedObject(true, 704, DERSequence(rootOfTrust)))
        teeEnforced.add(DERTaggedObject(true, 706, ASN1Integer(202401)))
        teeEnforced.add(
            DERTaggedObject(
                true,
                710,
                DEROctetString("OriginalBrand".toByteArray(Charsets.UTF_8)),
            ),
        )
        teeEnforced.add(DERTaggedObject(true, 718, ASN1Integer(20240205)))
        teeEnforced.add(DERTaggedObject(true, 719, ASN1Integer(20240305)))
        keyDesc.add(DERSequence(teeEnforced))

        val oid = ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17")
        builder.addExtension(oid, false, DERSequence(keyDesc))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return keyPair to certificate
    }
}
