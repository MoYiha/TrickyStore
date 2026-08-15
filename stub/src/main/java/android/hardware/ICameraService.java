package android.hardware;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/** Minimal compile-only mirror. */
public interface ICameraService extends IInterface {
    abstract class Stub extends Binder implements ICameraService {
        public static ICameraService asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
