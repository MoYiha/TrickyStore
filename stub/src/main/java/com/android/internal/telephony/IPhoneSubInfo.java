/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import android.os.IInterface;
import android.os.Binder;
import android.os.IBinder;

public interface IPhoneSubInfo extends IInterface {

    public static abstract class Stub extends Binder implements IPhoneSubInfo {
        public static IPhoneSubInfo asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }

    String getDeviceId(String callingPackage);
    String getDeviceIdForPhone(int phoneId, String callingPackage);
    String getImeiForSubscriber(int subId, String callingPackage);
    String getSubscriberId(String callingPackage);
    String getSubscriberIdForSubscriber(int subId, String callingPackage);
    String getIccSerialNumber(String callingPackage);
    String getIccSerialNumberForSubscriber(int subId, String callingPackage);
}
