package cleveres.tricky.cleverestech.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A simple, standalone CBOR encoder to support RKP structures.
 * Implements RFC 8949 subsets required for COSE/RKP.
 */
public class CborEncoder {
    
    // Major types
    private static final int MT_UNSIGNED = 0;
    private static final int MT_NEGATIVE = 1;
    private static final int MT_BYTE_STRING = 2;
    private static final int MT_TEXT_STRING = 3;
    private static final int MT_ARRAY = 4;
    private static final int MT_MAP = 5;
    private static final int MT_TAG = 6;
    private static final int MT_SIMPLE = 7;

    public static byte[] encode(Object object) {
        FastByteArrayOutputStream baos = new FastByteArrayOutputStream(256);
        try {
            encodeItem(baos, object);
        } catch (IOException e) {
            throw new RuntimeException("CBOR encoding failed", e);
        }
        return baos.toByteArray();
    }

    public static void encodeItem(OutputStream os, Object value) throws IOException {
        if (value == null) {
            encodeTypeAndLength(os, MT_SIMPLE, 22); // null
        } else if (value instanceof Integer) {
            encodeInteger(os, (Integer) value);
        } else if (value instanceof Long) {
            encodeInteger(os, (Long) value);
        } else if (value instanceof String) {
            byte[] bytes = ((String) value).getBytes(StandardCharsets.UTF_8);
            encodeTypeAndLength(os, MT_TEXT_STRING, bytes.length);
            os.write(bytes);
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            encodeTypeAndLength(os, MT_BYTE_STRING, bytes.length);
            os.write(bytes);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            encodeTypeAndLength(os, MT_ARRAY, list.size());
            for (Object item : list) {
                encodeItem(os, item);
            }
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            encodeTypeAndLength(os, MT_MAP, map.size());
            
            // Optimization: Use EncodedEntry to avoid repeated getBytes() calls during sorting.
            // This reduces allocations from O(N log N) to O(N).
            List<EncodedEntry> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(new EncodedEntry(entry.getKey(), entry.getValue()));
            }
            Collections.sort(entries);

            for (EncodedEntry entry : entries) {
                entry.writeKey(os);
                encodeItem(os, entry.value);
            }
        } else if (value instanceof Boolean) {
            boolean b = (Boolean) value;
            encodeTypeAndLength(os, MT_SIMPLE, b ? 21 : 20);
        } else {
            throw new IOException("Unknown type in CborEncoder: " + value.getClass().getSimpleName());
        }
    }

    private static void encodeInteger(OutputStream os, long value) throws IOException {
        if (value >= 0) {
            encodeTypeAndLength(os, MT_UNSIGNED, value);
        } else {
            encodeTypeAndLength(os, MT_NEGATIVE, -1 - value);
        }
    }

    /**
     * Optimization: Encode type and length directly to the stream.
     * Previously, this method allocated temporary byte arrays (byte[2] to byte[9]) for every integer.
     * By writing bytes directly using bit-shifting, we eliminate millions of small object allocations
     * during complex RKP structure encoding, significantly reducing GC pressure.
     */
    private static void encodeTypeAndLength(OutputStream os, int majorType, long value) throws IOException {
        int mt = majorType << 5;
        if (value < 24) {
            os.write(mt | (int) value);
        } else if (value <= 0xFF) {
            os.write(mt | 24);
            os.write((int) value);
        } else if (value <= 0xFFFF) {
            os.write(mt | 25);
            os.write((int) (value >> 8));
            os.write((int) value);
        } else if (value <= 0xFFFFFFFFL) {
            os.write(mt | 26);
            os.write((int) (value >> 24));
            os.write((int) (value >> 16));
            os.write((int) (value >> 8));
            os.write((int) value);
        } else {
            os.write(mt | 27);
            os.write((int) (value >> 56));
            os.write((int) (value >> 48));
            os.write((int) (value >> 40));
            os.write((int) (value >> 32));
            os.write((int) (value >> 24));
            os.write((int) (value >> 16));
            os.write((int) (value >> 8));
            os.write((int) value);
        }
    }

    private static class EncodedEntry implements Comparable<EncodedEntry> {
        final Object key;
        final Object value;
        final byte[] stringKeyBytes; // Null if key is not a string

        EncodedEntry(Object key, Object value) {
            this.key = key;
            this.value = value;
            if (key instanceof String) {
                this.stringKeyBytes = ((String) key).getBytes(StandardCharsets.UTF_8);
            } else {
                this.stringKeyBytes = null;
            }
        }

        @Override
        public int compareTo(EncodedEntry other) {
            Object k1 = this.key;
            Object k2 = other.key;

            // Compare Integers
            if (k1 instanceof Integer && k2 instanceof Integer) {
                int i1 = (Integer) k1;
                int i2 = (Integer) k2;
                // Check Major Types: Positive (MT0) < Negative (MT1)
                if (i1 >= 0 && i2 < 0) return -1;
                if (i1 < 0 && i2 >= 0) return 1;

                // Same Major Type
                if (i1 >= 0) {
                    // Both positive: 1 < 2
                    return Integer.compare(i1, i2);
                } else {
                    // Both negative: -1 (0) < -2 (1).
                    // Java compare(-1, -2) is 1. We want -1.
                    // So reverse comparison.
                    return Integer.compare(i2, i1);
                }
            }

            // Compare Strings (optimized)
            if (k1 instanceof String && k2 instanceof String) {
                byte[] b1 = this.stringKeyBytes;
                byte[] b2 = other.stringKeyBytes;
                if (b1.length != b2.length) {
                    return Integer.compare(b1.length, b2.length);
                }
                // Lexicographical comparison of bytes
                for (int i = 0; i < b1.length; i++) {
                    int diff = (b1[i] & 0xFF) - (b2[i] & 0xFF);
                    if (diff != 0) return diff;
                }
                return 0;
            }

            // Mixed keys: Int < String per standard CBOR canonical rules usually?
            // RFC 7049: "If two keys have different types, the one with the lower major type sorts earlier."
            // Int is major 0/1, String is major 3. So Int comes first.
            if (k1 instanceof Integer && k2 instanceof String) return -1;
            if (k1 instanceof String && k2 instanceof Integer) return 1;

            // Fallback for negatives (which are Integers in our logic usually)
            return 0;
        }

        void writeKey(OutputStream os) throws IOException {
             if (stringKeyBytes != null) {
                 encodeTypeAndLength(os, MT_TEXT_STRING, stringKeyBytes.length);
                 os.write(stringKeyBytes);
             } else {
                 // For non-string keys, fall back to standard encoding logic.
                 // Note: We avoid circular dependency by calling static method directly.
                 encodeItem(os, key);
             }
        }
    }
}
