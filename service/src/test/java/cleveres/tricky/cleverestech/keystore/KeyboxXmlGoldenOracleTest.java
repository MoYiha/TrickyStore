package cleveres.tricky.cleverestech.keystore;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cleveres.tricky.cleverestech.Logger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import org.junit.Test;

public class KeyboxXmlGoldenOracleTest {
    @Test
    public void sharedEcFixtureMatchesManagedOracle() throws Exception {
        Logger.setImpl(new Logger.LogImpl() {
            @Override public void d(String tag, String msg) { /* no-op */ }
            @Override public void e(String tag, String msg) { /* no-op */ }
            @Override public void e(String tag, String msg, Throwable t) { /* no-op */ }
            @Override public void i(String tag, String msg) { /* no-op */ }
        });

        String xml = readFixture();
        List<CertHack.KeyBox> parsed = CertHack.parseKeyboxXml(new StringReader(xml), "valid_ec.xml");

        assertEquals(1, parsed.size());
        CertHack.KeyBox keybox = parsed.get(0);
        assertTrue(isEcAlgorithm(keybox.keyPair().getPrivate().getAlgorithm()));
        assertTrue(isEcAlgorithm(keybox.keyPair().getPublic().getAlgorithm()));
        assertEquals(1, keybox.certificates().size());
        assertTrue(keybox.certificates().get(0) instanceof X509Certificate);
        X509Certificate leaf = (X509Certificate) keybox.certificates().get(0);
        assertArrayEquals(keybox.keyPair().getPublic().getEncoded(), leaf.getPublicKey().getEncoded());
    }

    private static boolean isEcAlgorithm(String algorithm) {
        return "EC".equalsIgnoreCase(algorithm) || "ECDSA".equalsIgnoreCase(algorithm);
    }

    private static String readFixture() throws Exception {
        InputStream input = Objects.requireNonNull(
                KeyboxXmlGoldenOracleTest.class.getResourceAsStream("/keybox/valid_ec.xml"),
                "missing shared keybox fixture");
        try (input; Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            StringBuilder output = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (count != 0) output.append(buffer, 0, count);
            }
            return output.toString();
        }
    }
}
