package cleveres.tricky.cleverestech.keystore;

import org.junit.After;
import org.junit.Test;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import cleveres.tricky.cleverestech.TestKeyboxFixtures;

public class ConcurrencyTest {

    @After
    public void tearDown() {
        ManagedKeyboxStateOracle.readFromXml(null);
    }

    private static final String VALID_XML = "<?xml version=\"1.0\"?>\n" +
            "<AndroidAttestation>\n" +
            "<NumberOfKeyboxes>1</NumberOfKeyboxes>\n" +
            "<Keybox>\n" +
            "<Key algorithm=\"ecdsa\">\n" +
            "<PrivateKey>\n" +
            TestKeyboxFixtures.INSTANCE.getEcPrivateKey() + "\n" +
            "</PrivateKey>\n" +
            "<CertificateChain>\n" +
            "<NumberOfCertificates>1</NumberOfCertificates>\n" +
            "<Certificate>\n" +
            TestKeyboxFixtures.INSTANCE.getCertificate() + "\n" +
            "</Certificate>\n" +
            "</CertificateChain>\n" +
            "</Key>\n" +
            "</Keybox>\n" +
            "</AndroidAttestation>";

    @Test
    public void testKeyboxesConcurrency() throws InterruptedException {
        ManagedKeyboxStateOracle.readFromXml(new StringReader(VALID_XML));
        assertTrue(CertHack.canHack());

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean failed = new AtomicBoolean(false);

        Thread reader = new Thread(() -> {
            while (running.get()) {
                if (!CertHack.canHack()) {
                    failed.set(true);
                }
            }
        });

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                ManagedKeyboxStateOracle.readFromXml(new StringReader(VALID_XML));
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed.set(true);
                    break;
                }
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
