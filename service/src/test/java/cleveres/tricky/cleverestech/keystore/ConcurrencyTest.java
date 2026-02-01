package cleveres.tricky.cleverestech.keystore;

import org.junit.Test;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class ConcurrencyTest {

    private static final String VALID_XML = "<?xml version=\"1.0\"?>\n" +
            "<AndroidAttestation>\n" +
            "<NumberOfKeyboxes>1</NumberOfKeyboxes>\n" +
            "<Keybox>\n" +
            "<Key algorithm=\"ecdsa\">\n" +
            "<PrivateKey>\n" +
            "-----BEGIN EC PRIVATE KEY-----\n" +
            "MHcCAQEEIAcPs+YkQGT6EDkaEH6Z9StSR7mQuKnh49K0DVqB/ZxYoAoGCCqGSM49\n" +
            "AwEHoUQDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAO\n" +
            "XHqvCjNpJSbUxgz3SZefi8TWWQ1t32G/1w==\n" +
            "-----END EC PRIVATE KEY-----\n" +
            "</PrivateKey>\n" +
            "<CertificateChain>\n" +
            "<NumberOfCertificates>1</NumberOfCertificates>\n" +
            "<Certificate>\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBfTCCASOgAwIBAgIUBZ47iWGUbx00hmWBPTYkakbXnigwCgYIKoZIzj0EAwIw\n" +
            "FDESMBAGA1UEAwwJVGVzdCBDZXJ0MB4XDTI2MDEyOTIxNTI0M1oXDTI3MDEyNDIx\n" +
            "NTI0M1owFDESMBAGA1UEAwwJVGVzdCBDZXJ0MFkwEwYHKoZIzj0CAQYIKoZIzj0D\n" +
            "AQcDQgAEzi23gXvUATkDmPcNPgsqe24eWmSIfuteSk8S5wJxs4ABt+O6QGAOXHqv\n" +
            "CjNpJSbUxgz3SZefi8TWWQ1t32G/16NTMFEwHQYDVR0OBBYEFCwifKyDaNaHtKvx\n" +
            "m+0eLn/LZoTaMB8GA1UdIwQYMBaAFCwifKyDaNaHtKvxm+0eLn/LZoTaMA8GA1Ud\n" +
            "EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgT+CWCLXuIN5XY0c3mFN1p1FM\n" +
            "1KAiK9pMwjbHYxNxDmYCIQDXriCpaafMnkJIqGb8UsI5XlkQD0soXYP7hd9ymW/t\n" +
            "qg==\n" +
            "-----END CERTIFICATE-----\n" +
            "</Certificate>\n" +
            "</CertificateChain>\n" +
            "</Key>\n" +
            "</Keybox>\n" +
            "</AndroidAttestation>";

    @Test
    public void testKeyboxesConcurrency() throws InterruptedException {
        // Initialize with valid keybox
        CertHack.readFromXml(new StringReader(VALID_XML));
        assertTrue(CertHack.canHack());

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean failed = new AtomicBoolean(false);

        Thread reader = new Thread(() -> {
            while (running.get()) {
                if (!CertHack.canHack()) {
                    failed.set(true);
                    // running.set(false); // Don't stop immediately to stress more
                }
                // Also could try hackCertificateChain if I could mock args, but canHack() checks !keyboxes.isEmpty()
                // If readFromXml clears it, canHack() returns false.
            }
        });

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                CertHack.readFromXml(new StringReader(VALID_XML));
                try { Thread.sleep(1); } catch (InterruptedException e) {}
            }
            running.set(false);
        });

        reader.start();
        writer.start();

        reader.join();
        writer.join();

        assertFalse("CertHack.canHack() returned false during reload (race condition)", failed.get());
    }
}
