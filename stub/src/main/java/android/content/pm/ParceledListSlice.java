package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

public class ParceledListSlice<T extends Parcelable> implements Parcelable {
    public List<T> getList() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new RuntimeException("Stub!");
    }

    public static final Creator<ParceledListSlice> CREATOR = new Creator<ParceledListSlice>() {
        @Override
        public ParceledListSlice createFromParcel(Parcel source) {
            throw new RuntimeException("Stub!");
        }

        @Override
        public ParceledListSlice[] newArray(int size) {
            throw new RuntimeException("Stub!");
        }
    };
}
