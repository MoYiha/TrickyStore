package cleveres.tricky.cleverestech.keystore;

import org.junit.Test;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import cleveres.tricky.cleverestech.Logger;
import cleveres.tricky.cleverestech.UtilKt;
import cleveres.tricky.cleverestech.keystore.CertHack;

public class CertHackTest {

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
    public void testReadFromXml() {
        // Setup Logger to print to stdout so we can see what happens
        Logger.setImpl(new Logger.LogImpl() {
            @Override public void d(String tag, String msg) { System.out.println("D/" + tag + ": " + msg); }
            @Override public void e(String tag, String msg) { System.out.println("E/" + tag + ": " + msg); }
            @Override public void e(String tag, String msg, Throwable t) { System.out.println("E/" + tag + ": " + msg); t.printStackTrace(); }
            @Override public void i(String tag, String msg) { System.out.println("I/" + tag + ": " + msg); }
        });

        String xml = "<?xml version=\"1.0\"?>\n" +
                "<AndroidAttestation>\n" +
                "<NumberOfKeyboxes>1</NumberOfKeyboxes>\n" +
                "<Keybox>\n" +
                "<Key algorithm=\"ecdsa\">\n" +
                "<PrivateKey>\n" + EC_KEY + "\n</PrivateKey>\n" +
                "<CertificateChain>\n" +
                "<NumberOfCertificates>1</NumberOfCertificates>\n" +
                "<Certificate>\n" + TEST_CERT + "\n</Certificate>\n" +
                "</CertificateChain>\n" +
                "</Key>\n" +
                "</Keybox>\n" +
                "</AndroidAttestation>";

        CertHack.readFromXml(new StringReader(xml));

        assertTrue("Keybox should be loaded", CertHack.canHack());
    }

    private int findSequence(byte[] data, byte[] sequence) {
        for (int i = 0; i <= data.length - sequence.length; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) {
                if (data[i + j] != sequence[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    @Test
    public void testDeviceInfoVbmetaDigest() {
        // Generate DeviceInfo CBOR
        byte[] cbor = CertHack.createDeviceInfoCbor("Google", "Google", "generic", "Pixel", "generic");

        assertNotNull("CBOR should not be null", cbor);

        // Search for "vbmeta_digest" key encoded in CBOR
        // "vbmeta_digest" is 13 chars.
        // CBOR String header: Major Type 3 (Text), Length 13 -> 0x6D
        byte[] keyBytes = "vbmeta_digest".getBytes(StandardCharsets.UTF_8);
        byte[] searchPattern = new byte[keyBytes.length + 1];
        searchPattern[0] = (byte) 0x6D;
        System.arraycopy(keyBytes, 0, searchPattern, 1, keyBytes.length);

        int keyIndex = findSequence(cbor, searchPattern);
        assertTrue("vbmeta_digest key not found in CBOR", keyIndex != -1);

        // Value follows the key.
        // Expected Value header: Major Type 2 (Bytes), Length 32 -> 0x58 0x20
        int valueIndex = keyIndex + searchPattern.length;
        assertEquals("Expected byte string header 0x58", (byte)0x58, cbor[valueIndex]);
        assertEquals("Expected byte string length 0x20", (byte)0x20, cbor[valueIndex + 1]);

        // The actual 32 bytes of the digest
        byte[] digest = Arrays.copyOfRange(cbor, valueIndex + 2, valueIndex + 2 + 32);

        // Current Fix: Digest should match UtilKt.getBootHash()
        byte[] expectedHash = UtilKt.getBootHash();

        assertArrayEquals("Digest should match UtilKt.getBootHash()", expectedHash, digest);

        // Ensure it is not just zeros (unless bootHash is zeros, which it shouldn't be)
        byte[] zeros = new byte[32];
        assertFalse("Digest should not be all zeros", Arrays.equals(zeros, digest));
    }
}
