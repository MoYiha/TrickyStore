package cleveres.tricky.cleverestech.keystore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import cleveres.tricky.cleverestech.Config;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import org.junit.Assert;
import java.util.Collections;

@RunWith(JUnit4.class)
public class CertHackOrderTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private void setAttestationId(String tag, byte[] value) throws Exception {
        Field field = Config.class.getDeclaredField("attestationIds");
        field.setAccessible(true);
        Map<String, byte[]> map = new HashMap<>();
        if (value != null) {
            map.put(tag, value);
        }
        field.set(Config.INSTANCE, map);
    }

    // Reset state to avoid pollution
    private void resetConfig() throws Exception {
        setAttestationId("BRAND", null);
        Field moduleHash = Config.class.getDeclaredField("moduleHash");
        moduleHash.setAccessible(true);
        moduleHash.set(Config.INSTANCE, null);
    }

    private X509Certificate generateCertWith718(KeyPair kp) throws Exception {
        X500Name issuer = new X500Name("CN=Test");
        BigInteger serial = BigInteger.ONE;
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 100000);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, issuer, kp.getPublic());

        ASN1EncodableVector keyDesc = new ASN1EncodableVector();
        keyDesc.add(new ASN1Integer(100)); // version
        keyDesc.add(new ASN1Enumerated(1)); // security level
        keyDesc.add(new ASN1Integer(100));
        keyDesc.add(new ASN1Enumerated(1));
        keyDesc.add(new DEROctetString(new byte[0])); // challenge
        keyDesc.add(new DEROctetString(new byte[0])); // uniqueId
        keyDesc.add(new DERSequence()); // softwareEnforced

        ASN1EncodableVector teeEnforced = new ASN1EncodableVector();
        // Add RootOfTrust (704)
        ASN1EncodableVector rootOfTrust = new ASN1EncodableVector();
        rootOfTrust.add(new DEROctetString(new byte[32])); // key
        rootOfTrust.add(ASN1Boolean.TRUE);
        rootOfTrust.add(new ASN1Enumerated(0));
        rootOfTrust.add(new DEROctetString(new byte[32])); // hash
        teeEnforced.add(new DERTaggedObject(true, 704, new DERSequence(rootOfTrust)));

        // Add Vendor Patch Level (718)
        teeEnforced.add(new DERTaggedObject(true, 718, new ASN1Integer(20240101)));

        keyDesc.add(new DERSequence(teeEnforced));

        ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17");
        builder.addExtension(OID, false, new DERSequence(keyDesc));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    @Test
    public void testAttestationIdOrdering() throws Exception {
        resetConfig();
        // Setup: We override BRAND (710). Original cert has VendorPatchLevel (718).
        // Result should be: [ ..., 710, ..., 718 ]
        // Buggy Result: [ ..., 718, 710 ]

        setAttestationId("BRAND", "Google".getBytes());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = generateCertWith718(kp);

        // Inject keybox
        CertHack.KeyBox keyBox = new CertHack.KeyBox(kp, Collections.singletonList(cert), "test.xml");

        // Create new state via reflection
        Map<String, List<CertHack.KeyBox>> newKeyboxes = new java.util.HashMap<>();
        newKeyboxes.put("RSA", Collections.singletonList(keyBox));
        Map<String, List<CertHack.KeyBox>> newKeyboxFiles = new java.util.HashMap<>();

        Class<?> stateClass = Class.forName("cleveres.tricky.cleverestech.keystore.CertHack$State");
        Constructor<?> ctor = stateClass.getDeclaredConstructor(Map.class, Map.class);
        ctor.setAccessible(true);
        Object newState = ctor.newInstance(newKeyboxes, newKeyboxFiles);

        Field stateField = CertHack.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(null, newState);

        Certificate[] hackedChain = CertHack.hackCertificateChain(new Certificate[] { cert }, 0);

        X509Certificate hackedCert = (X509Certificate) hackedChain[0];
        byte[] extBytes = hackedCert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17");
        ASN1Primitive extStruct = ASN1Primitive.fromByteArray(ASN1OctetString.getInstance(extBytes).getOctets());
        ASN1Sequence seq = ASN1Sequence.getInstance(extStruct);
        ASN1Sequence teeEnforced = (ASN1Sequence) seq.getObjectAt(7);

        int lastTag = -1;
        boolean found710 = false;
        boolean found718 = false;

        for(ASN1Encodable e : teeEnforced) {
            ASN1TaggedObject t = (ASN1TaggedObject) e;
            int tag = t.getTagNo();
            // Ignore other tags for this check
            if (tag == 710) found710 = true;
            if (tag == 718) found718 = true;

            if (tag == 710 || tag == 718) {
                System.out.println("Found tag: " + tag);
                if (lastTag != -1) {
                    Assert.assertTrue("Tags out of order: " + lastTag + " came before " + tag, lastTag < tag);
                }
                lastTag = tag;
            }
        }
        Assert.assertTrue("BRAND (710) missing", found710);
        Assert.assertTrue("VendorPatchLevel (718) missing", found718);
    }
}
