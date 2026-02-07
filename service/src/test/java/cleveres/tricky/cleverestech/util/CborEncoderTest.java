package cleveres.tricky.cleverestech.util;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;

public class CborEncoderTest {

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    @Test
    public void testMapSortingOrder() {
        // Map { 1: "a", -1: "b" }
        // CBOR:
        // 1 (unsigned) -> 0x01
        // -1 (negative) -> 0x20
        // Expected order: 1 (Major Type 0) comes before -1 (Major Type 1)

        Map<Integer, String> map = new HashMap<>();
        map.put(1, "a");
        map.put(-1, "b");

        byte[] encoded = CborEncoder.encode(map);

        // Expected:
        // A2       (Map, size 2)
        // 01       (Key: 1)
        // 61 61    (Val: "a")
        // 20       (Key: -1)
        // 61 62    (Val: "b")
        byte[] expected = new byte[] {
            (byte)0xA2,
            (byte)0x01, (byte)0x61, (byte)0x61,
            (byte)0x20, (byte)0x61, (byte)0x62
        };

        // If bug exists (Integer.compare used):
        // -1 comes before 1
        // A2
        // 20       (Key: -1)
        // 61 62    (Val: "b")
        // 01       (Key: 1)
        // 61 61    (Val: "a")

        System.out.println("Encoded: " + bytesToHex(encoded));
        assertArrayEquals("CBOR Map sorting is incorrect!", expected, encoded);
    }

    @Test
    public void testStringMapSortingOrder() {
        // Map { "aa": 1, "a": 2, "b": 3 }
        // Expected sort: "a", "b", "aa" (shorter length first)

        Map<String, Integer> map = new HashMap<>();
        map.put("aa", 1);
        map.put("a", 2);
        map.put("b", 3);

        byte[] encoded = CborEncoder.encode(map);

        // Expected:
        // A3       (Map 3)
        // 61 61    ("a")
        // 02       (2)
        // 61 62    ("b")
        // 03       (3)
        // 62 61 61 ("aa")
        // 01       (1)

        byte[] expected = new byte[] {
            (byte)0xA3,
            (byte)0x61, (byte)0x61, (byte)0x02,
            (byte)0x61, (byte)0x62, (byte)0x03,
            (byte)0x62, (byte)0x61, (byte)0x61, (byte)0x01
        };

        System.out.println("Encoded Strings: " + bytesToHex(encoded));
        assertArrayEquals("CBOR String Map sorting is incorrect!", expected, encoded);
    }
}
