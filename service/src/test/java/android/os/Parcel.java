package android.os;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Parcel {
    public static final AtomicInteger obtainCount = new AtomicInteger(0);
    private ArrayList<Object> data = new ArrayList<>();
    private int pos = 0;

    public static Parcel obtain() {
        obtainCount.incrementAndGet();
        return new Parcel();
    }

    public static void resetStats() {
        obtainCount.set(0);
    }

    public void recycle() {
        data.clear();
        pos = 0;
    }

    public int dataSize() {
        return data.size();
    }

    public int dataPosition() {
        return pos;
    }

    public void setDataPosition(int pos) {
        this.pos = pos;
    }

    public void writeNoException() {}
    public void readException() {}

    public <T> T readTypedObject(Parcelable.Creator<T> c) {
        return null;
    }
    public void writeTypedObject(Parcelable val, int parcelableFlags) {}
    public void enforceInterface(String interfaceName) {}

    // Methods for test setup
    public void pushInt(int val) {
        data.add(val);
    }
    public void pushLong(long val) {
        data.add(val);
    }
    public void pushBinder(IBinder val) {
        data.add(val);
    }

    // Read methods
    public int readInt() {
        if (pos >= data.size()) return 0;
        Object o = data.get(pos++);
        return o instanceof Integer ? (Integer) o : 0;
    }

    public long readLong() {
        if (pos >= data.size()) return 0L;
        Object o = data.get(pos++);
        if (o instanceof Integer) return ((Integer)o).longValue();
        return o instanceof Long ? (Long) o : 0L;
    }

    public IBinder readStrongBinder() {
        if (pos >= data.size()) return null;
        Object o = data.get(pos++);
        return o instanceof IBinder ? (IBinder) o : null;
    }

    // Write methods
    public void writeInt(int val) {
        data.add(val);
    }
    public void writeLong(long val) {
        data.add(val);
    }
    public void writeStrongBinder(IBinder val) {
        data.add(val);
    }

    public void appendFrom(Parcel other, int offset, int length) {
        if (other == null) return;
        // Simple mock: we assume offset is index, length is count
        for (int i = 0; i < length; i++) {
            if (offset + i < other.data.size()) {
                data.add(other.data.get(offset + i));
            } else {
                 // padding or ignore
            }
        }
    }
}
