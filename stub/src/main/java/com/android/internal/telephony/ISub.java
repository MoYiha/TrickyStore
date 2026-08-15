/* Minimal compile-only mirror of Android's hidden ISub surface used by the service. */
package com.android.internal.telephony;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.telephony.SubscriptionInfo;
import java.util.List;

public interface ISub extends IInterface {
    abstract class Stub extends Binder implements ISub {
        public static ISub asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }

    List<SubscriptionInfo> getActiveSubscriptionInfoList(
            String callingPackage, String callingFeatureId, boolean isForAllProfiles);
    int getActiveSubInfoCount(
            String callingPackage, String callingFeatureId, boolean isForAllProfiles);
    int getActiveSubInfoCountMax();
}
