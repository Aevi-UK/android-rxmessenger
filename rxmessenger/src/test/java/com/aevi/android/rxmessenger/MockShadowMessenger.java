/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aevi.android.rxmessenger;

import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.List;

@Implements(Messenger.class)
public class MockShadowMessenger {

    private Handler handler;

    private static List<Message> messages = new ArrayList<>();

    public void __constructor__(Handler handler) {
        this.handler = handler;
    }

    @Implementation
    public void send(Message message) throws RemoteException {
        if (handler != null) {
            message.setTarget(handler);
            message.sendToTarget();
        }
        messages.add(message);
    }

    public static List<Message> getMessages() {
        return messages;
    }

    public static void clearMessages() {
        messages.clear();
    }
}
