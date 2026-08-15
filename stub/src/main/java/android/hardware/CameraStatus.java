package android.hardware;

import android.os.Parcel;
import android.os.Parcelable;

/** Minimal compile-only mirror. Runtime classes come from Android. */
public class CameraStatus implements Parcelable {
    public String cameraId;
    public int status;

    /** Present on modern Android; runtime code accesses this field reflectively for compatibility. */
    public int deviceId;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new UnsupportedOperationException();
    }

    public static final Parcelable.Creator<CameraStatus> CREATOR =
            new Parcelable.Creator<CameraStatus>() {
                @Override
                public CameraStatus createFromParcel(Parcel source) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CameraStatus[] newArray(int size) {
                    return new CameraStatus[size];
                }
            };
}
