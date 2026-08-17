package cleveres.tricky.cleverestech.keystore;

import android.security.keystore.KeyProperties;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import cleveres.tricky.cleverestech.CertificateBackend;
import cleveres.tricky.cleverestech.Config;
import cleveres.tricky.cleverestech.Logger;
import cleveres.tricky.cleverestech.PolicyState;
import cleveres.tricky.cleverestech.UtilKt;

public final class CertHack {
    private static final int MAX_KEYBOXES_PER_FILE = 64;
    private static final int MAX_KEYS_PER_KEYBOX = 4;
    private static final int MAX_CERTIFICATES_PER_CHAIN = 16;
    private static final int MAX_PEM_CHARS = 256 * 1024;
    private static final int MAX_CERTIFICATE_CACHE_ENTRIES = 64;
    private static final int MAX_LEAF_CERTIFICATE_BYTES = 64 * 1024;
    private static final String[] ATTESTATION_ID_NAMES =
            {"BRAND", "DEVICE", "PRODUCT", "SERIAL", "IMEI", "MEID", "MANUFACTURER", "MODEL", "IMEI2"};
    private static final int[] ATTESTATION_ID_TAGS = {710, 711, 712, 713, 714, 715, 716, 717, 723};

    private static final ThreadLocal<CertificateFactory> CERTIFICATE_FACTORY =
            new ThreadLocal<CertificateFactory>() {
                @Override
                protected CertificateFactory initialValue() {
                    try {
                        return CertificateFactory.getInstance("X.509");
                    } catch (Exception e) {
                        throw new IllegalStateException("X.509 certificate factory is unavailable", e);
                    }
                }
            };

    private static final class PreparedKeyBox {
        final String signatureAlgorithm;
        final Certificate[] issuerChain;

        PreparedKeyBox(KeyBox keybox) throws Exception {
            if (keybox.certificates.isEmpty()) throw new IOException("Keybox has no certificates");
            this.signatureAlgorithm = signatureAlgorithmForKeybox(keybox);
            if (this.signatureAlgorithm == null) throw new IOException("Unsupported keybox algorithm");
            this.issuerChain = keybox.certificates.toArray(new Certificate[0]);
            byte[] encoded = keybox.keyPair.getPrivate().getEncoded();
            if (encoded == null || encoded.length == 0) throw new IOException("Keybox private key is not encodable");
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static class State {
        final Map<String, List<KeyBox>> keyboxes;
        final Map<String, List<KeyBox>> keyboxFiles;
        final Map<KeyBox, PreparedKeyBox> preparedKeyboxes;
        final Map<CacheKey, Certificate[]> certificateCache;

        State(Map<String, List<KeyBox>> keyboxes, Map<String, List<KeyBox>> keyboxFiles) {
            this.keyboxes = immutableLists(keyboxes);
            this.keyboxFiles = immutableLists(keyboxFiles);
            IdentityHashMap<KeyBox, PreparedKeyBox> prepared = new IdentityHashMap<>();
            for (List<KeyBox> list : this.keyboxes.values()) {
                for (KeyBox keybox : list) {
                    if (prepared.containsKey(keybox)) continue;
                    try {
                        prepared.put(keybox, new PreparedKeyBox(keybox));
                    } catch (Exception error) {
                        Logger.e("Could not prepare keybox metadata", error);
                    }
                }
            }
            this.preparedKeyboxes = Collections.unmodifiableMap(prepared);
            this.certificateCache = Collections.synchronizedMap(
                    new LinkedHashMap<CacheKey, Certificate[]>(32, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<CacheKey, Certificate[]> eldest) {
                            return size() > MAX_CERTIFICATE_CACHE_ENTRIES;
                        }
                    });
        }

        private static Map<String, List<KeyBox>> immutableLists(Map<String, List<KeyBox>> source) {
            Map<String, List<KeyBox>> copy = new HashMap<>();
            source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            return Map.copyOf(copy);
        }
    }

    private static volatile State state = new State(Collections.emptyMap(), Collections.emptyMap());

    static {
        // Retained only for the legacy XML compatibility oracle. Production keybox parsing and all
        // certificate/attestation DER rewriting run in Rust.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static boolean canHack() {
        return !state.keyboxes.isEmpty();
    }

    public static int getKeyboxCount() {
        int count = 0;
        for (List<KeyBox> list : state.keyboxes.values()) count += list.size();
        return count;
    }

    private static KeyPair parseKeyPair(String key, PublicKey leafPublicKey) throws Throwable {
        try (PEMParser parser = new PEMParser(new StringReader(UtilKt.trimLine(key)))) {
            Object parsed = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (parsed instanceof PEMKeyPair pemKeyPair) return converter.getKeyPair(pemKeyPair);
            if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                return new KeyPair(leafPublicKey, converter.getPrivateKey(privateKeyInfo));
            }
            throw new IOException("Unsupported private-key PEM object");
        }
    }

    private static Certificate parseCert(String cert) throws Throwable {
        try (PemReader reader = new PemReader(new StringReader(UtilKt.trimLine(cert)))) {
            var pemObject = reader.readPemObject();
            if (pemObject == null) throw new IOException("Certificate PEM is empty");
            return CERTIFICATE_FACTORY.get().generateCertificate(new ByteArrayInputStream(pemObject.getContent()));
        }
    }

    private static final class CacheKey {
        private final byte[] leafEncoded;
        private final int hashCode;

        CacheKey(byte[] leafEncoded) {
            this.leafEncoded = Objects.requireNonNull(leafEncoded, "leafEncoded");
            this.hashCode = Arrays.hashCode(this.leafEncoded);
        }

        int indexForPool(int size) {
            if (size <= 0) throw new IllegalArgumentException("Keybox pool is empty");
            return (hashCode & 0x7FFFFFFF) % size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Arrays.equals(leafEncoded, ((CacheKey) o).leafEncoded);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    static Map<Integer, byte[]> selectPresentAttestationIdOverrides(
            Map<Integer, byte[]> configured,
            List<Integer> originalTags
    ) {
        if (configured.isEmpty() || originalTags.isEmpty()) return Collections.emptyMap();
        Map<Integer, byte[]> selected = new HashMap<>();
        for (Integer tag : originalTags) {
            byte[] value = configured.get(tag);
            if (value != null) selected.put(tag, value);
        }
        return selected;
    }

    static String signingKeyAlgorithm(String signatureAlgorithm) {
        if (signatureAlgorithm == null) return null;
        String normalized = signatureAlgorithm.toUpperCase(Locale.ROOT);
        if (normalized.contains("ECDSA")) return KeyProperties.KEY_ALGORITHM_EC;
        if (normalized.contains("RSA")) return KeyProperties.KEY_ALGORITHM_RSA;
        return null;
    }

    public static List<KeyBox> parseKeyboxXml(Reader reader) {
        return parseKeyboxXml(reader, "unknown.xml");
    }

    /** Legacy managed oracle retained for compatibility tests; production loading uses KeyboxLoader/Rust. */
    public static List<KeyBox> parseKeyboxXml(Reader reader, String filename) {
        if (reader == null) return Collections.emptyList();
        List<KeyBox> parsedList = new ArrayList<>();
        try {
            XMLParser xmlParser = new XMLParser(reader);
            XMLParser.Element root = xmlParser.getRoot();
            if (root == null || !"AndroidAttestation".equals(root.name)) return Collections.emptyList();

            XMLParser.Element numKeyboxes = root.getChild("NumberOfKeyboxes");
            if (numKeyboxes == null || numKeyboxes.getText() == null) return Collections.emptyList();
            List<XMLParser.Element> keyboxes = root.getChildren("Keybox");
            int declaredKeyboxes = Integer.parseInt(Objects.requireNonNull(numKeyboxes.getText()));
            if (declaredKeyboxes < 1 || declaredKeyboxes > MAX_KEYBOXES_PER_FILE || keyboxes.size() != declaredKeyboxes) {
                Logger.e("Keybox count is invalid or does not match the XML declaration");
                return Collections.emptyList();
            }

            for (XMLParser.Element keybox : keyboxes) {
                List<XMLParser.Element> keys = keybox.getChildren("Key");
                if (keys.isEmpty() || keys.size() > MAX_KEYS_PER_KEYBOX) return Collections.emptyList();
                for (XMLParser.Element key : keys) {
                    String keyboxAlgorithm = key.attributes.get("algorithm");
                    XMLParser.Element privateKeyElement = key.getChild("PrivateKey");
                    String privateKey = privateKeyElement != null ? privateKeyElement.getText() : null;
                    if (privateKey == null || privateKey.length() > MAX_PEM_CHARS) return Collections.emptyList();

                    XMLParser.Element certChain = key.getChild("CertificateChain");
                    if (certChain == null) return Collections.emptyList();
                    XMLParser.Element numCertsElement = certChain.getChild("NumberOfCertificates");
                    if (numCertsElement == null || numCertsElement.getText() == null) return Collections.emptyList();
                    int numberOfCertificates = Integer.parseInt(Objects.requireNonNull(numCertsElement.getText()));
                    if (numberOfCertificates < 1 || numberOfCertificates > MAX_CERTIFICATES_PER_CHAIN) {
                        return Collections.emptyList();
                    }
                    List<XMLParser.Element> certificates = certChain.getChildren("Certificate");
                    if (certificates.size() != numberOfCertificates) {
                        Logger.e("Keybox certificate count does not match its declaration");
                        return Collections.emptyList();
                    }
                    LinkedList<Certificate> certificateChain = new LinkedList<>();
                    for (int j = 0; j < numberOfCertificates; j++) {
                        String certPem = certificates.get(j).getText();
                        if (certPem == null || certPem.length() > MAX_PEM_CHARS) {
                            certificateChain.clear();
                            break;
                        }
                        certificateChain.add(parseCert(certPem));
                    }
                    if (certificateChain.size() != numberOfCertificates) return Collections.emptyList();
                    KeyPair pair = parseKeyPair(privateKey, certificateChain.getFirst().getPublicKey());
                    if (!isValidKeybox(pair, certificateChain, keyboxAlgorithm)) return Collections.emptyList();
                    parsedList.add(new KeyBox(pair, certificateChain, filename));
                }
            }
            return parsedList;
        } catch (Throwable t) {
            Logger.e("Error parsing xml: " + t.getClass().getName());
            return Collections.emptyList();
        }
    }

    private static boolean isValidKeybox(
            KeyPair keyPair,
            List<Certificate> certificateChain,
            String declaredAlgorithm
    ) {
        try {
            if (keyPair == null || certificateChain.isEmpty() || !(certificateChain.get(0) instanceof X509Certificate leaf)) {
                return false;
            }
            String actualAlgorithm = keyPair.getPublic().getAlgorithm();
            if (!(actualAlgorithm.equalsIgnoreCase("EC") || actualAlgorithm.equalsIgnoreCase("ECDSA") ||
                    actualAlgorithm.equalsIgnoreCase("RSA"))) return false;
            if (declaredAlgorithm == null || !(declaredAlgorithm.equalsIgnoreCase(actualAlgorithm) ||
                    (declaredAlgorithm.equalsIgnoreCase("ecdsa") && actualAlgorithm.equalsIgnoreCase("EC")))) {
                return false;
            }
            if (!Arrays.equals(keyPair.getPublic().getEncoded(), leaf.getPublicKey().getEncoded())) return false;
            Signature proof = Signature.getInstance(
                    actualAlgorithm.equalsIgnoreCase("RSA") ? "SHA256withRSA" : "SHA256withECDSA");
            byte[] challenge = "CleveresTricky keybox validation".getBytes(StandardCharsets.UTF_8);
            proof.initSign(keyPair.getPrivate());
            proof.update(challenge);
            byte[] signature = proof.sign();
            try {
                proof.initVerify(leaf.getPublicKey());
                proof.update(challenge);
                if (!proof.verify(signature)) return false;
            } finally {
                Arrays.fill(signature, (byte) 0);
            }
            for (int i = 0; i < certificateChain.size(); i++) {
                if (!(certificateChain.get(i) instanceof X509Certificate certificate)) return false;
                certificate.checkValidity();
                if (i + 1 < certificateChain.size()) certificate.verify(certificateChain.get(i + 1).getPublicKey());
            }
            return true;
        } catch (Exception e) {
            Logger.e("Keybox cryptographic validation failed: " + e.getClass().getSimpleName());
            return false;
        }
    }

    public static synchronized void setKeyboxes(List<KeyBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            Logger.i("clear all keyboxes");
            state = new State(Collections.emptyMap(), Collections.emptyMap());
            return;
        }
        Map<String, List<KeyBox>> newKeyboxes = new HashMap<>();
        Map<String, List<KeyBox>> newKeyboxFiles = new HashMap<>();
        for (KeyBox box : boxes) {
            String algo = normalizeAlgorithm(box.keyPair.getPublic().getAlgorithm());
            if (algo == null) {
                Logger.e("Ignoring unsupported keybox algorithm: " + box.keyPair.getPublic().getAlgorithm());
                continue;
            }
            newKeyboxes.computeIfAbsent(algo, ignored -> new ArrayList<>()).add(box);
            newKeyboxFiles.computeIfAbsent(box.filename, ignored -> new ArrayList<>()).add(box);
        }
        int ecCount = newKeyboxes.getOrDefault(KeyProperties.KEY_ALGORITHM_EC, Collections.emptyList()).size();
        int rsaCount = newKeyboxes.getOrDefault(KeyProperties.KEY_ALGORITHM_RSA, Collections.emptyList()).size();
        Logger.i("update keyboxes: total=" + boxes.size() + " (EC=" + ecCount + ", RSA=" + rsaCount + ")");
        state = new State(newKeyboxes, newKeyboxFiles);
    }

    public static void readFromXml(Reader reader) {
        if (reader == null) {
            setKeyboxes(Collections.emptyList());
            return;
        }
        setKeyboxes(parseKeyboxXml(reader));
    }

    public static void clearCertificateCache() {
        State currentState = state;
        synchronized (currentState.certificateCache) {
            currentState.certificateCache.clear();
        }
    }

    public static boolean hasCachedCertificateChains() {
        return !state.certificateCache.isEmpty();
    }

    public static Certificate[] getCachedCertificateChain(Certificate[] caList) {
        if (caList == null || caList.length == 0 || caList[0] == null) return null;
        try {
            byte[] leafEncoded = caList[0].getEncoded();
            if (leafEncoded.length == 0 || leafEncoded.length > MAX_LEAF_CERTIFICATE_BYTES) return null;
            Certificate[] cached = state.certificateCache.get(new CacheKey(leafEncoded));
            return cached == null ? null : cached.clone();
        } catch (Throwable error) {
            Logger.e("Could not resolve a cached attestation chain", error);
            return null;
        }
    }

    /**
     * Rewrites one key's attestation chain once per policy snapshot. Portable X.509/DER inspection,
     * authorization-list rewriting and signing are performed by the unprivileged Rust backend.
     * Managed code only resolves Android policy, selects JCA key material and materializes the final
     * X.509 certificate object.
     */
    public static Certificate[] hackCertificateChain(Certificate[] caList, int uid) {
        if (caList == null || caList.length == 0 || caList[0] == null) {
            throw new UnsupportedOperationException("Certificate chain is empty");
        }
        CertificateBackend.Inspection inspection = null;
        byte[] privateKeyDer = null;
        try {
            State currentState = state;
            byte[] leafEncoded = caList[0].getEncoded();
            if (leafEncoded.length == 0 || leafEncoded.length > MAX_LEAF_CERTIFICATE_BYTES) {
                Logger.e("Attestation leaf certificate has an invalid size");
                return caList;
            }
            CacheKey cacheKey = new CacheKey(leafEncoded);
            Map<CacheKey, Certificate[]> cache = currentState.certificateCache;
            synchronized (cache) {
                Certificate[] cached = cache.get(cacheKey);
                if (cached != null) return cached.clone();
            }

            inspection = CertificateBackend.inspect(leafEncoded);
            if (inspection == null) return caList;
            Config.AttestationPatchLevels patchLevels = PolicyState.INSTANCE.resolveAttestationPatchLevels(
                    uid,
                    inspection.getSystemPatch(),
                    inspection.getVendorPatch(),
                    inspection.getBootPatch());

            String preferredSignerAlgorithm = KeyProperties.KEY_ALGORITHM_EC;
            var appConfig = Config.INSTANCE.getAppConfig(uid);
            List<KeyBox> list;
            if (appConfig != null && appConfig.getKeyboxFilename() != null) {
                list = selectKeyboxPool(
                        currentState.keyboxFiles.get(appConfig.getKeyboxFilename()), preferredSignerAlgorithm);
            } else {
                list = selectGlobalKeyboxPool(currentState, preferredSignerAlgorithm);
            }
            if (list.isEmpty()) throw new UnsupportedOperationException("No compatible keybox is available");

            KeyBox keybox = list.get(cacheKey.indexForPool(list.size()));
            PreparedKeyBox prepared = currentState.preparedKeyboxes.get(keybox);
            if (prepared == null) throw new UnsupportedOperationException("Keybox metadata is unavailable");
            int signingAlgorithm = signingWireAlgorithm(prepared.signatureAlgorithm);
            if (signingAlgorithm == 0) return caList;

            byte[] verifiedBootKey = firstUsableBootDigest(
                    UtilKt.getBootKey(), inspection.getOriginalBootKey(), UtilKt.getPersistentBootKey());
            byte[] verifiedBootHash = firstUsableBootDigest(
                    UtilKt.getBootHash(), inspection.getOriginalBootHash(), UtilKt.getPersistentBootHash());
            if (verifiedBootKey == null || verifiedBootHash == null) {
                Logger.e("Verified boot key/hash is unavailable; preserving the original certificate chain");
                return caList;
            }

            Map<Integer, byte[]> idOverrides = presentIdOverrides(uid, inspection.getPresentIdMask());
            byte[] moduleHash = inspection.getSupportsModuleHash() ? Config.INSTANCE.getModuleHash() : null;
            byte[] issuerCertificateDer = prepared.issuerChain[0].getEncoded();
            privateKeyDer = keybox.keyPair.getPrivate().getEncoded();
            if (privateKeyDer == null || privateKeyDer.length == 0) return caList;

            byte[] rewrittenDer = CertificateBackend.rewrite(
                    leafEncoded,
                    issuerCertificateDer,
                    privateKeyDer,
                    signingAlgorithm,
                    patchDisposition(patchLevels.getSystem()), patchLevels.getSystem().getValue(),
                    patchDisposition(patchLevels.getVendor()), patchLevels.getVendor().getValue(),
                    patchDisposition(patchLevels.getBoot()), patchLevels.getBoot().getValue(),
                    idOverrides,
                    moduleHash,
                    verifiedBootKey,
                    verifiedBootHash);
            if (rewrittenDer == null) return caList;
            Certificate rewrittenLeaf = CERTIFICATE_FACTORY.get().generateCertificate(
                    new ByteArrayInputStream(rewrittenDer));
            Certificate[] result = new Certificate[prepared.issuerChain.length + 1];
            result[0] = rewrittenLeaf;
            System.arraycopy(prepared.issuerChain, 0, result, 1, prepared.issuerChain.length);
            synchronized (cache) {
                Certificate[] raced = cache.get(cacheKey);
                if (raced != null) return raced.clone();
                cache.put(cacheKey, result.clone());
            }
            return result;
        } catch (Throwable t) {
            Logger.e("Exception in hackCertificateChain", t);
            return caList;
        } finally {
            if (privateKeyDer != null) Arrays.fill(privateKeyDer, (byte) 0);
            if (inspection != null) inspection.wipe();
        }
    }

    private static Map<Integer, byte[]> presentIdOverrides(int uid, int mask) {
        Map<Integer, byte[]> overrides = new HashMap<>();
        for (int index = 0; index < ATTESTATION_ID_TAGS.length; index++) {
            if ((mask & (1 << index)) == 0) continue;
            byte[] value = Config.INSTANCE.getAttestationId(ATTESTATION_ID_NAMES[index], uid);
            if (value != null) overrides.put(ATTESTATION_ID_TAGS[index], value);
        }
        return overrides;
    }

    private static int patchDisposition(Config.AttestationPatchComponent component) {
        return switch (component.getDisposition()) {
            case KEEP -> CertificateBackend.PATCH_KEEP;
            case OMIT -> CertificateBackend.PATCH_OMIT;
            case REPLACE -> CertificateBackend.PATCH_REPLACE;
        };
    }

    private static int signingWireAlgorithm(String signatureAlgorithm) {
        if ("SHA256withECDSA".equals(signatureAlgorithm)) return CertificateBackend.SIGNING_EC_P256_SHA256;
        if ("SHA256withRSA".equals(signatureAlgorithm)) return CertificateBackend.SIGNING_RSA_PKCS1_SHA256;
        return 0;
    }

    private static byte[] firstUsableBootDigest(byte[] preferred, byte[] original, byte[] persistent) {
        byte[] value = usableBootDigest(preferred);
        if (value != null) return value;
        value = usableBootDigest(original);
        if (value != null) return value;
        return usableBootDigest(persistent);
    }

    private static byte[] usableBootDigest(byte[] value) {
        if (value == null || value.length != 32) return null;
        int aggregate = 0;
        for (byte current : value) aggregate |= current & 0xFF;
        return aggregate == 0 ? null : value;
    }

    private static List<KeyBox> selectKeyboxPool(List<KeyBox> candidates, String preferredAlgorithm) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();
        if (preferredAlgorithm != null) {
            List<KeyBox> preferred = filterKeyboxesByAlgorithm(candidates, preferredAlgorithm);
            if (!preferred.isEmpty()) return preferred;
        }
        String fallbackAlgorithm = KeyProperties.KEY_ALGORITHM_EC.equals(preferredAlgorithm)
                ? KeyProperties.KEY_ALGORITHM_RSA : KeyProperties.KEY_ALGORITHM_EC;
        List<KeyBox> fallback = filterKeyboxesByAlgorithm(candidates, fallbackAlgorithm);
        if (!fallback.isEmpty()) return fallback;
        return filterKeyboxesByAlgorithm(candidates, KeyProperties.KEY_ALGORITHM_RSA);
    }

    private static List<KeyBox> selectGlobalKeyboxPool(State currentState, String preferredAlgorithm) {
        if (preferredAlgorithm != null) {
            List<KeyBox> preferred = currentState.keyboxes.get(preferredAlgorithm);
            if (preferred != null && !preferred.isEmpty()) return preferred;
        }
        String fallbackAlgorithm = KeyProperties.KEY_ALGORITHM_EC.equals(preferredAlgorithm)
                ? KeyProperties.KEY_ALGORITHM_RSA : KeyProperties.KEY_ALGORITHM_EC;
        List<KeyBox> fallback = currentState.keyboxes.get(fallbackAlgorithm);
        if (fallback != null && !fallback.isEmpty()) return fallback;
        List<KeyBox> rsa = currentState.keyboxes.get(KeyProperties.KEY_ALGORITHM_RSA);
        return rsa == null ? Collections.emptyList() : rsa;
    }

    private static String signatureAlgorithmForKeybox(KeyBox keybox) {
        String algorithm = normalizeAlgorithm(keybox.keyPair.getPrivate().getAlgorithm());
        if (KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)) return "SHA256withECDSA";
        if (KeyProperties.KEY_ALGORITHM_RSA.equals(algorithm)) return "SHA256withRSA";
        return null;
    }

    private static String normalizeAlgorithm(String algorithm) {
        if (algorithm == null) return null;
        if (algorithm.equalsIgnoreCase("EC") || algorithm.equalsIgnoreCase("ECDSA")) {
            return KeyProperties.KEY_ALGORITHM_EC;
        }
        if (algorithm.equalsIgnoreCase("RSA")) return KeyProperties.KEY_ALGORITHM_RSA;
        return null;
    }

    private static List<KeyBox> filterKeyboxesByAlgorithm(List<KeyBox> candidates, String requiredAlgorithm) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();
        List<KeyBox> matches = new ArrayList<>();
        for (KeyBox candidate : candidates) {
            if (requiredAlgorithm.equals(normalizeAlgorithm(candidate.keyPair.getPublic().getAlgorithm()))) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    public record KeyBox(KeyPair keyPair, List<Certificate> certificates, String filename) {
        public KeyBox {
            Objects.requireNonNull(keyPair, "keyPair");
            certificates = List.copyOf(Objects.requireNonNull(certificates, "certificates"));
            filename = Objects.requireNonNull(filename, "filename");
        }
    }
}
