package android.os;

public class Parcel {
    public static Parcel obtain() {
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
}
