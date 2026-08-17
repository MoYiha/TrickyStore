package cleveres.tricky.cleverestech.keystore;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Test-only managed/BC oracle used for differential characterization of the Rust keybox path. */
public class CertHackDifferentialBaselineTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void generatedEcKeyboxParsesAndVerifies() throws Exception {
        GeneratedKeybox fixture = generateKeybox("EC");

        List<CertHack.KeyBox> parsed = ManagedKeyboxOracle.parse(
                new StringReader(keyboxXml("ecdsa", fixture.privateKeyPem, List.of(fixture.certificatePem))),
                "generated-ec.xml");

        assertEquals(1, parsed.size());
        assertEquals("generated-ec.xml", parsed.get(0).filename());
        assertEquals("EC", parsed.get(0).keyPair().getPublic().getAlgorithm());
        assertEquals(1, parsed.get(0).certificates().size());
        assertArrayEquals(fixture.pair.getPublic().getEncoded(), parsed.get(0).keyPair().getPublic().getEncoded());
        assertKeyPairWorks(parsed.get(0).keyPair(), "SHA256withECDSA");
        X509Certificate parsedCertificate = (X509Certificate) parsed.get(0).certificates().get(0);
        parsedCertificate.verify(parsedCertificate.getPublicKey());
    }

    @Test
    public void generatedRsaKeyboxParsesAndVerifies() throws Exception {
        GeneratedKeybox fixture = generateKeybox("RSA");

        List<CertHack.KeyBox> parsed = ManagedKeyboxOracle.parse(
                new StringReader(keyboxXml("rsa", fixture.privateKeyPem, List.of(fixture.certificatePem))),
                "generated-rsa.xml");

        assertEquals(1, parsed.size());
        assertEquals("generated-rsa.xml", parsed.get(0).filename());
        assertEquals("RSA", parsed.get(0).keyPair().getPublic().getAlgorithm());
        assertEquals(1, parsed.get(0).certificates().size());
        assertArrayEquals(fixture.pair.getPublic().getEncoded(), parsed.get(0).keyPair().getPublic().getEncoded());
        assertKeyPairWorks(parsed.get(0).keyPair(), "SHA256withRSA");
        X509Certificate parsedCertificate = (X509Certificate) parsed.get(0).certificates().get(0);
        parsedCertificate.verify(parsedCertificate.getPublicKey());
    }

    @Test
    public void mismatchedPrivateKeyAndLeafPublicKeyRejectsWholeKeybox() throws Exception {
        GeneratedKeybox certificateOwner = generateKeybox("EC");
        GeneratedKeybox wrongPrivateKey = generateKeybox("EC");
        String xml = keyboxXml(
                "ecdsa",
                wrongPrivateKey.privateKeyPem,
                List.of(certificateOwner.certificatePem));

        assertTrue(ManagedKeyboxOracle.parse(new StringReader(xml)).isEmpty());
    }

    @Test
    public void certificateChainWithUnrelatedIssuerRejectsWholeKeybox() throws Exception {
        GeneratedKeybox leaf = generateKeybox("RSA");
        GeneratedKeybox unrelatedIssuer = generateKeybox("RSA");
        String xml = keyboxXml(
                "rsa",
                leaf.privateKeyPem,
                List.of(leaf.certificatePem, unrelatedIssuer.certificatePem));

        assertTrue(ManagedKeyboxOracle.parse(new StringReader(xml)).isEmpty());
    }

    @Test
    public void declaredAlgorithmMustMatchCryptographicKeyType() throws Exception {
        GeneratedKeybox fixture = generateKeybox("EC");
        String xml = keyboxXml("rsa", fixture.privateKeyPem, List.of(fixture.certificatePem));

        assertTrue(ManagedKeyboxOracle.parse(new StringReader(xml)).isEmpty());
    }

    @Test
    public void truncatedCertificatePemRejectsWholeKeybox() throws Exception {
        GeneratedKeybox fixture = generateKeybox("RSA");
        String truncated = fixture.certificatePem.substring(0, fixture.certificatePem.length() / 2);
        String xml = keyboxXml("rsa", fixture.privateKeyPem, List.of(truncated));

        assertTrue(ManagedKeyboxOracle.parse(new StringReader(xml)).isEmpty());
    }

    private static void assertKeyPairWorks(KeyPair pair, String signatureAlgorithm) throws Exception {
        byte[] challenge = "CleveresTricky certificate differential baseline".getBytes(StandardCharsets.UTF_8);
        Signature signature = Signature.getInstance(signatureAlgorithm);
        signature.initSign(pair.getPrivate());
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(pair.getPublic());
        signature.update(challenge);
        assertTrue(signature.verify(signed));
    }

    private static GeneratedKeybox generateKeybox(String algorithm) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        String signatureAlgorithm;
        if ("EC".equals(algorithm)) {
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            signatureAlgorithm = "SHA256withECDSA";
        } else {
            generator.initialize(2048, RANDOM);
            signatureAlgorithm = "SHA256withRSA";
        }
        KeyPair pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=CleveresTricky differential test " + algorithm);
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                new BigInteger(128, RANDOM).add(BigInteger.ONE),
                Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)),
                subject,
                pair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(pair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);
        certificate.verify(pair.getPublic());
        return new GeneratedKeybox(
                pair,
                pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                pem("CERTIFICATE", certificate.getEncoded()));
    }

    private static String keyboxXml(String declaredAlgorithm, String privateKeyPem, List<String> certificates) {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\"?>\n")
                .append("<AndroidAttestation>\n")
                .append("<NumberOfKeyboxes>1</NumberOfKeyboxes>\n")
                .append("<Keybox>\n")
                .append("<Key algorithm=\"").append(declaredAlgorithm).append("\">\n")
                .append("<PrivateKey>\n").append(privateKeyPem).append("\n</PrivateKey>\n")
                .append("<CertificateChain>\n")
                .append("<NumberOfCertificates>").append(certificates.size()).append("</NumberOfCertificates>\n");
        for (String certificate : certificates) {
            xml.append("<Certificate>\n").append(certificate).append("\n</Certificate>\n");
        }
        return xml.append("</CertificateChain>\n")
                .append("</Key>\n")
                .append("</Keybox>\n")
                .append("</AndroidAttestation>")
                .toString();
    }

    private static String pem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----";
    }

    private record GeneratedKeybox(KeyPair pair, String privateKeyPem, String certificatePem) {}
}
