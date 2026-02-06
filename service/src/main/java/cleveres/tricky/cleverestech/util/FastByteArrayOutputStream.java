package cleveres.tricky.cleverestech.util;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * A non-synchronized implementation of ByteArrayOutputStream.
 * Standard ByteArrayOutputStream methods are synchronized, which introduces
 * unnecessary overhead when thread safety is not required (e.g., local variables).
 */
public class FastByteArrayOutputStream extends ByteArrayOutputStream {

    public FastByteArrayOutputStream() {
        this(32);
    }

    public FastByteArrayOutputStream(int size) {
        super(size);
    }

    @Override
    public void write(int b) {
        if (count >= buf.length) {
            grow(count + 1);
        }
        buf[count++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) - b.length > 0) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return;
        }
        if (len > buf.length - count) {
            grow(count + len);
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    @Override
    public byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    private void grow(int minCapacity) {
        int oldCapacity = buf.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        if (newCapacity < 0) {
            if (minCapacity < 0) // overflow
                throw new OutOfMemoryError();
            newCapacity = Integer.MAX_VALUE;
        }
        buf = Arrays.copyOf(buf, newCapacity);
    }
}
