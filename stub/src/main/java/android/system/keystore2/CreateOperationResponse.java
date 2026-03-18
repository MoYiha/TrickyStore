package android.system.keystore2;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class CreateOperationResponse implements Parcelable {
    public IKeystoreOperation iOperation;
    public OperationChallenge operationChallenge;

    public static final Creator<CreateOperationResponse> CREATOR = new Creator<CreateOperationResponse>() {
        @Override
        public CreateOperationResponse createFromParcel(Parcel in) {
            throw new RuntimeException("");
        }

        @Override
        public CreateOperationResponse[] newArray(int size) {
            throw new RuntimeException("");
        }
    };

    @Override
    public int describeContents() {
        throw new RuntimeException("");
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        throw new RuntimeException("");
    }
}
