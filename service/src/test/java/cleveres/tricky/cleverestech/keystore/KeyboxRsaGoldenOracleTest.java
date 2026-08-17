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

/** Test-only RSA fixture used to pin the managed parser/JCA oracle during Rust migration. */
public class KeyboxRsaGoldenOracleTest {
    @Test
    public void sharedRsaFixtureMatchesManagedOracle() throws Exception {
        Logger.setImpl(new Logger.LogImpl() {
            @Override public void d(String tag, String msg) { /* no-op */ }
            @Override public void e(String tag, String msg) { /* no-op */ }
            @Override public void e(String tag, String msg, Throwable t) { /* no-op */ }
            @Override public void i(String tag, String msg) { /* no-op */ }
        });

        List<CertHack.KeyBox> parsed =
                CertHack.parseKeyboxXml(new StringReader(readFixture()), "valid_rsa.xml");
        assertEquals(1, parsed.size());
        CertHack.KeyBox keybox = parsed.get(0);
        assertEquals("RSA", keybox.keyPair().getPrivate().getAlgorithm());
        assertEquals("RSA", keybox.keyPair().getPublic().getAlgorithm());
        assertEquals(1, keybox.certificates().size());
        assertTrue(keybox.certificates().get(0) instanceof X509Certificate);
        X509Certificate leaf = (X509Certificate) keybox.certificates().get(0);
        assertArrayEquals(keybox.keyPair().getPublic().getEncoded(), leaf.getPublicKey().getEncoded());
        leaf.checkValidity();
    }

    private static String readFixture() throws Exception {
        InputStream input = Objects.requireNonNull(
                KeyboxRsaGoldenOracleTest.class.getResourceAsStream("/keybox/valid_rsa.xml"),
                "missing shared RSA keybox fixture");
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
