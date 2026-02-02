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
}
