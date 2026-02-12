package cleveres.tricky.cleverestech.keystore;

import org.junit.Test;
import java.io.StringReader;
import static org.junit.Assert.assertEquals;
import cleveres.tricky.cleverestech.Logger;

public class MultipleKeysTest {

    private static final String EC_KEY = "-----BEGIN EC PRIVATE KEY-----\n" +
            "MHcCAQEEIAcPs+YkQGT6EDkaEH6Z9StSR7mQuKnh49K0DVqB/ZxYoAoGCCqGSM49\n" +
            "AwEHoUQDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAO\n" +
            "XHqvCjNpJSbUxgz3SZefi8TWWQ1t32G/1w==\n" +
            "-----END EC PRIVATE KEY-----";

    private static final String TEST_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBfTCCASOgAwIBAgIUBZ47iWGUbx00hmWBPTYkakbXnigwCgYIKoZIzj0EAwIw\n" +
            "FDESMBAGA1UEAwwJVGVzdCBDZXJ0MB4XDTI2MDEyOTIxNTI0M1oXDTI3MDEyNDIx\n" +
            "NTI0M1owFDESMBAGA1UEAwwJVGVzdCBDZXJ0MFkwEwYHKoZIzj0CAQYIKoZIzj0D\n" +
            "AQcDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAOXHqv\n" +
            "CjNpJSbUxgz3SZefi8TWWQ1t32G/16NTMFEwHQYDVR0OBBYEFCwifKyDaNaHtKvx\n" +
            "m+0eLn/LZoTaMB8GA1UdIwQYMBaAFCwifKyDaNaHtKvxm+0eLn/LZoTaMA8GA1Ud\n" +
            "EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgT+CWCLXuIN5XY0c3mFN1p1FM\n" +
            "1KAiK9pMwjbHYxNxDmYCIQDXriCpaafMnkJIqGb8UsI5XlkQD0soXYP7hd9ymW/t\n" +
            "qg==\n" +
            "-----END CERTIFICATE-----";

    @Test
    public void testMultipleKeysInMultipleKeyboxes() {
        Logger.setImpl(new Logger.LogImpl() {
            @Override public void d(String tag, String msg) { }
            @Override public void e(String tag, String msg) { System.out.println("E/" + tag + ": " + msg); }
            @Override public void e(String tag, String msg, Throwable t) { System.out.println("E/" + tag + ": " + msg); t.printStackTrace(); }
            @Override public void i(String tag, String msg) { }
        });

        // Scenario: 2 Keyboxes.
        // Keybox 1 has 2 Keys.
        // Keybox 2 has 1 Key.
        // Total Keys = 3.
        // We set NumberOfKeyboxes to 3 (hoping to load all 3 keys).

        String xml = "<?xml version=\"1.0\"?>\n" +
                "<AndroidAttestation>\n" +
                "<NumberOfKeyboxes>3</NumberOfKeyboxes>\n" +
                "<Keybox>\n" +
                "  <Key algorithm=\"ecdsa\">\n" +
                "    <PrivateKey>\n" + EC_KEY + "\n</PrivateKey>\n" +
                "    <CertificateChain>\n" +
                "      <NumberOfCertificates>1</NumberOfCertificates>\n" +
                "      <Certificate>\n" + TEST_CERT + "\n</Certificate>\n" +
                "    </CertificateChain>\n" +
                "  </Key>\n" +
                "  <Key algorithm=\"ecdsa\">\n" +
                "    <PrivateKey>\n" + EC_KEY + "\n</PrivateKey>\n" +
                "    <CertificateChain>\n" +
                "      <NumberOfCertificates>1</NumberOfCertificates>\n" +
                "      <Certificate>\n" + TEST_CERT + "\n</Certificate>\n" +
                "    </CertificateChain>\n" +
                "  </Key>\n" +
                "</Keybox>\n" +
                "<Keybox>\n" +
                "  <Key algorithm=\"ecdsa\">\n" +
                "    <PrivateKey>\n" + EC_KEY + "\n</PrivateKey>\n" +
                "    <CertificateChain>\n" +
                "      <NumberOfCertificates>1</NumberOfCertificates>\n" +
                "      <Certificate>\n" + TEST_CERT + "\n</Certificate>\n" +
                "    </CertificateChain>\n" +
                "  </Key>\n" +
                "</Keybox>\n" +
                "</AndroidAttestation>";

        // Current implementation logic:
        // actualKeyboxElements = 2.
        // i=0: Keybox[0].Key -> First key of KB1. OK.
        // i=1: Keybox[1].Key -> First key of KB2. OK.
        // i=2: i (2) >= actual (2). Else branch.
        //      Keybox.Key[2].
        //      This tries to find 3rd key in KB1 (or whatever Keybox matches).
        //      KB1 has 2 keys. Key[2] doesn't exist.
        //      KB2 has 1 key. Key[2] doesn't exist.
        // So it likely fails to load the 3rd key (which is actually the 2nd key of KB1, but we skipped it).
        // Wait, Key[1] of KB1 was skipped because at i=1 we jumped to KB2.

        CertHack.readFromXml(new StringReader(xml));

        assertEquals("Should load 3 keys", 3, CertHack.getKeyboxCount());
    }
}
