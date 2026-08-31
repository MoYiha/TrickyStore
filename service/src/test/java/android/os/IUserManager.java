package android.os;

public interface IUserManager {
    int[] getUserIds();

    int[] getProfileIds(int userId, boolean enabledOnly);

    class Stub {
        public static IUserManager asInterface(IBinder binder) {
            return null;
        }
    }
}
