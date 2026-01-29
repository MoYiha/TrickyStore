package android.os;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

public class Parcel {
    public static final AtomicInteger obtainCount = new AtomicInteger(0);

    public static void resetStats() {
        obtainCount.set(0);
    }

    public static Parcel obtain() {
        obtainCount.incrementAndGet();
        return new Parcel();
    }

    public void recycle() {}

    public int dataSize() {
        return 100; // Simulated size
    }

    public void writeNoException() {}

    public void readException() {}

    public <T> T readTypedObject(Parcelable.Creator<T> c) {
        return null;
    }

    public void writeTypedObject(Parcelable val, int parcelableFlags) {}

    public void enforceInterface(String interfaceName) {}

    public void appendFrom(Parcel p, int offset, int length) {}
    public void setDataPosition(int pos) {}
    public int dataPosition() { return 0; }
    public void writeInt(int i) {}
    public void writeLong(long l) {}
    public void writeStrongBinder(IBinder val) {}

    // Methods used by BinderInterceptorTest to mock reading data
    private List<Object> data = new ArrayList<>();
    private int readPos = 0;

    public void pushInt(int i) {
        data.add(i);
    }

    public void pushLong(long l) {
        data.add(l);
    }

    public void pushBinder(IBinder b) {
        data.add(b);
    }

    public int readInt() {
        if (readPos < data.size() && data.get(readPos) instanceof Integer) {
             return (Integer) data.get(readPos++);
        }
        return 0;
    }

    public long readLong() {
        if (readPos < data.size() && data.get(readPos) instanceof Long) {
             return (Long) data.get(readPos++);
        }
        return 0L;
    }

    public IBinder readStrongBinder() {
        if (readPos < data.size() && data.get(readPos) instanceof IBinder) {
             return (IBinder) data.get(readPos++);
        }
        return null;
    }
}
