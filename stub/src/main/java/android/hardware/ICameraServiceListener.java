package android.hardware;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/** Minimal compile-only mirror. */
public interface ICameraServiceListener extends IInterface {
    abstract class Stub extends Binder implements ICameraServiceListener {
        public static ICameraServiceListener asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
