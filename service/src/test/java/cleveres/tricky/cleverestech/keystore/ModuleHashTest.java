package cleveres.tricky.cleverestech.keystore;

import cleveres.tricky.cleverestech.Config;

import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class ModuleHashTest {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static Field moduleHashField() throws Exception {
        Field field = Config.class.getDeclaredField("moduleHash");
        field.setAccessible(true);
        return field;
    }

    private static Field certHackStateField() throws Exception {
        Field field = CertHack.class.getDeclaredField("state");
        field.setAccessible(true);
        return field;
    }

    private X509Certificate generateSelfSignedCert(KeyPair kp) throws Exception {
        X500Name issuer = new X500Name("CN=Test");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.ONE,
                new Date(System.currentTimeMillis() - 1000),
                new Date(System.currentTimeMillis() + 60_000),
                issuer,
                kp.getPublic());

        ASN1EncodableVector keyDesc = new ASN1EncodableVector();
        keyDesc.add(new ASN1Integer(400));
        keyDesc.add(new ASN1Enumerated(1));
        keyDesc.add(new ASN1Integer(400));
        keyDesc.add(new ASN1Enumerated(1));
        keyDesc.add(new DEROctetString(new byte[0]));
        keyDesc.add(new DEROctetString(new byte[0]));
        keyDesc.add(new DERSequence());

        ASN1EncodableVector rootOfTrust = new ASN1EncodableVector();
        rootOfTrust.add(new DEROctetString(new byte[32]));
        rootOfTrust.add(ASN1Boolean.TRUE);
        rootOfTrust.add(new ASN1Enumerated(0));
        rootOfTrust.add(new DEROctetString(new byte[32]));
        ASN1EncodableVector teeEnforced = new ASN1EncodableVector();
        teeEnforced.add(new DERTaggedObject(true, 704, new DERSequence(rootOfTrust)));
        keyDesc.add(new DERSequence(teeEnforced));

        builder.addExtension(
                new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"),
                false,
                new DERSequence(keyDesc));
        String signatureAlgorithm = kp.getPrivate().getAlgorithm().equalsIgnoreCase("RSA")
                ? "SHA256withRSA"
                : "SHA256withECDSA";
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    @Test
    public void testHackCertificateChainWithModuleHash() throws Exception {
        Field moduleHash = moduleHashField();
        Field state = certHackStateField();
        byte[] previousHash = (byte[]) moduleHash.get(Config.INSTANCE);
        Object previousState = state.get(null);
        byte[] expectedHash = new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};

        try {
            moduleHash.set(Config.INSTANCE, expectedHash);

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            X509Certificate cert = generateSelfSignedCert(kp);
            CertHack.KeyBox keyBox = new CertHack.KeyBox(kp, Collections.singletonList(cert), "test.xml");

            Map<String, List<CertHack.KeyBox>> newKeyboxes = new HashMap<>();
            newKeyboxes.put("RSA", Collections.singletonList(keyBox));
            Map<String, List<CertHack.KeyBox>> newKeyboxFiles = new HashMap<>();
            newKeyboxFiles.put("test.xml", Collections.singletonList(keyBox));

            Class<?> stateClass = Class.forName("cleveres.tricky.cleverestech.keystore.CertHack$State");
            Constructor<?> ctor = stateClass.getDeclaredConstructor(Map.class, Map.class);
            ctor.setAccessible(true);
            state.set(null, ctor.newInstance(newKeyboxes, newKeyboxFiles));

            Certificate[] hackedChain = CertHack.hackCertificateChain(new Certificate[] {cert}, 0);
            X509Certificate hackedCert = (X509Certificate) hackedChain[0];
            byte[] extBytes = hackedCert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17");
            ASN1Primitive extStruct = ASN1Primitive.fromByteArray(
                    ASN1OctetString.getInstance(extBytes).getOctets());
            ASN1Sequence seq = ASN1Sequence.getInstance(extStruct);
            ASN1Sequence softwareEnforced = (ASN1Sequence) seq.getObjectAt(6);
            ASN1Sequence teeEnforced = (ASN1Sequence) seq.getObjectAt(7);

            boolean found = false;
            for (ASN1Encodable encodable : softwareEnforced) {
                ASN1TaggedObject taggedObject = (ASN1TaggedObject) encodable;
                if (taggedObject.getTagNo() == 724) {
                    found = true;
                    ASN1OctetString value = ASN1OctetString.getInstance(taggedObject.getBaseObject());
                    Assert.assertArrayEquals(expectedHash, value.getOctets());
                }
            }
            Assert.assertTrue("ModuleHash tag 724 not found", found);
            for (ASN1Encodable encodable : teeEnforced) {
                Assert.assertNotEquals(724, ((ASN1TaggedObject) encodable).getTagNo());
            }
        } finally {
            state.set(null, previousState);
            moduleHash.set(Config.INSTANCE, previousHash);
            CertHack.clearCertificateCache();
        }
    }

    @Test
    public void testCrossAlgorithmKeyboxFallback() throws Exception {
        Field moduleHash = moduleHashField();
        Field state = certHackStateField();
        byte[] previousHash = (byte[]) moduleHash.get(Config.INSTANCE);
        Object previousState = state.get(null);

        try {
            moduleHash.set(Config.INSTANCE, null);
            KeyPairGenerator rsaGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            rsaGenerator.initialize(2048);
            KeyPair rsaPair = rsaGenerator.generateKeyPair();
            X509Certificate attestationLeaf = generateSelfSignedCert(rsaPair);

            KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC", "BC");
            ecGenerator.initialize(256);
            KeyPair ecPair = ecGenerator.generateKeyPair();
            X509Certificate keyboxCertificate = generateSelfSignedCert(ecPair);
            CertHack.KeyBox keyBox = new CertHack.KeyBox(
                    ecPair, Collections.singletonList(keyboxCertificate), "ec-only.xml");

            Map<String, List<CertHack.KeyBox>> newKeyboxes = new HashMap<>();
            newKeyboxes.put("EC", Collections.singletonList(keyBox));
            Map<String, List<CertHack.KeyBox>> newKeyboxFiles = new HashMap<>();
            newKeyboxFiles.put("ec-only.xml", Collections.singletonList(keyBox));

            Class<?> stateClass = Class.forName("cleveres.tricky.cleverestech.keystore.CertHack$State");
            Constructor<?> ctor = stateClass.getDeclaredConstructor(Map.class, Map.class);
            ctor.setAccessible(true);
            state.set(null, ctor.newInstance(newKeyboxes, newKeyboxFiles));

            Certificate[] hackedChain = CertHack.hackCertificateChain(
                    new Certificate[] {attestationLeaf}, 0);
            Assert.assertEquals(2, hackedChain.length);
            X509Certificate hackedLeaf = (X509Certificate) hackedChain[0];
            hackedLeaf.verify(ecPair.getPublic());
            Assert.assertEquals("EC", CertHack.signingKeyAlgorithm(hackedLeaf.getSigAlgName()));
            Assert.assertArrayEquals(
                    attestationLeaf.getPublicKey().getEncoded(), hackedLeaf.getPublicKey().getEncoded());
        } finally {
            state.set(null, previousState);
            moduleHash.set(Config.INSTANCE, previousHash);
            CertHack.clearCertificateCache();
        }
    }
}
