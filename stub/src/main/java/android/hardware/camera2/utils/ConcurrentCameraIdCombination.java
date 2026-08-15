package android.hardware.camera2.utils;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Set;

/** Minimal compile-only mirror whose erased Set return type is stable across API levels. */
public class ConcurrentCameraIdCombination implements Parcelable {
    public Set<?> getConcurrentCameraIdCombination() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new UnsupportedOperationException();
    }

    public static final Parcelable.Creator<ConcurrentCameraIdCombination> CREATOR =
            new Parcelable.Creator<ConcurrentCameraIdCombination>() {
                @Override
                public ConcurrentCameraIdCombination createFromParcel(Parcel source) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ConcurrentCameraIdCombination[] newArray(int size) {
                    return new ConcurrentCameraIdCombination[size];
                }
            };
}
