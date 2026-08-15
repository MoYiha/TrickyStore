package android.hardware;

import android.os.Parcel;
import android.os.Parcelable;

/** Minimal compile-only mirror. */
public class CameraStatus implements Parcelable {
    public String cameraId;

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
