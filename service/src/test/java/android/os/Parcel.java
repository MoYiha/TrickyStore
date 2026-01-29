package android.os;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedList;

public class Parcel {
    public static final AtomicInteger obtainCount = new AtomicInteger(0);

    public static Parcel obtain() {
        obtainCount.incrementAndGet();
        return new Parcel();
    }

    public static void resetStats() {
        obtainCount.set(0);
    }

    public void recycle() {}

    public int dataSize() {
        return items.size() * 4; // Mock size
    }

    public void writeNoException() {}

    public void readException() {}

    public <T> T readTypedObject(Parcelable.Creator<T> c) {
        return null;
    }

    public void writeTypedObject(Parcelable val, int parcelableFlags) {}

    public void enforceInterface(String interfaceName) {}

    private LinkedList<Object> items = new LinkedList<>();

    // Write methods
    public void writeInt(int val) { items.add(val); }
    public void writeLong(long val) { items.add(val); }
    public void writeStrongBinder(IBinder val) { items.add(val); }

    // Read methods
    public int readInt() {
        if (items.isEmpty()) return 0;
        Object o = items.poll();
        if (o instanceof Integer) return (Integer) o;
        return 0;
    }
    public long readLong() {
        if (items.isEmpty()) return 0;
        Object o = items.poll();
        if (o instanceof Long) return (Long) o;
        return 0;
    }
    public IBinder readStrongBinder() {
        if (items.isEmpty()) return null;
        Object o = items.poll();
        if (o instanceof IBinder) return (IBinder) o;
        return null;
    }

    // Test helper methods
    public void pushInt(int val) { writeInt(val); }
    public void pushLong(long val) { writeLong(val); }
    public void pushBinder(IBinder val) { writeStrongBinder(val); }

    public void setDataPosition(int pos) {}
    public int dataPosition() { return 0; }

    public void appendFrom(Parcel parcel, int offset, int length) {
        // In a real implementation this would copy bytes.
        // For our mock, we can just copy items if any, or do nothing.
        // BinderInterceptor logic copies from 'data' to 'theData'.
        // The test doesn't populate 'data' with payload items, just header items.
        // So we can leave this empty or just copy everything (which might be nothing if we consumed everything).
        this.items.addAll(parcel.items);
    }
}
