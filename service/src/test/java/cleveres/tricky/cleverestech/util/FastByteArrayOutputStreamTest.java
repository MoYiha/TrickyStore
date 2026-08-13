package cleveres.tricky.cleverestech.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FastByteArrayOutputStreamTest {
    @Test
    public void growthErasesReplacedBufferWithoutChangingOutput() {
        InspectableStream output = new InspectableStream(4);
        output.write(new byte[]{1, 2, 3}, 0, 3);
        byte[] replaced = output.internalBuffer();

        output.write(new byte[]{4, 5, 6, 7}, 0, 4);

        assertArrayEquals(new byte[4], replaced);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7}, output.toByteArray());
    }

    @Test
    public void wipeErasesAllocatedCapacityAndResetsStream() {
        InspectableStream output = new InspectableStream(16);
        output.write(new byte[]{9, 8, 7}, 0, 3);
        byte[] allocated = output.internalBuffer();

        output.wipe();

        assertArrayEquals(new byte[16], allocated);
        assertEquals(0, output.size());
        output.write(6);
        assertArrayEquals(new byte[]{6}, output.toByteArray());
    }

    private static final class InspectableStream extends FastByteArrayOutputStream {
        InspectableStream(int size) {
            super(size);
        }

        byte[] internalBuffer() {
            return buf;
        }
    }
}
