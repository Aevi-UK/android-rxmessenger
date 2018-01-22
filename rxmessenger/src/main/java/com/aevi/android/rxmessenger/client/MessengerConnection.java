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
package com.aevi.android.rxmessenger.client;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.*;
import android.util.Log;

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

import static com.aevi.android.rxmessenger.MessageConstants.*;

class MessengerConnection implements ServiceConnection {

    private static final String TAG = MessengerConnection.class.getSimpleName();

    private final IncomingHandler incomingHandler;
    private final String clientId;
    private final BehaviorSubject<MessengerConnection> bindSubject = BehaviorSubject.create();

    private Messenger outgoingMessenger;
    private ComponentName componentName;
    private boolean bound = false;

    MessengerConnection(IncomingHandler incomingHandler) {
        this.incomingHandler = incomingHandler;
        this.clientId = UUID.randomUUID().toString();
        Log.d(TAG, "Created connection with id: " + clientId);
    }

    public void onServiceConnected(ComponentName componentName, IBinder binder) {
        if (componentName != null) {
            Log.d(TAG, "Bound to service - " + componentName.flattenToString());
        }
        this.componentName = componentName;
        outgoingMessenger = new Messenger(binder);
        bound = true;
        bindSubject.onNext(this);
    }

    public void onServiceDisconnected(ComponentName className) {
        if (className != null) {
            Log.d(TAG, "Unbound from service - " + className.flattenToString());
        }
        bound = false;
        bindSubject.onComplete();
    }

    String getClientId() {
        return clientId;
    }

    Observable<MessengerConnection> getConnectedObservable() {
        return bindSubject;
    }

    boolean isBound() {
        return bound;
    }

    void sendMessage(String requestData) {
        Log.d(TAG, "Sending message from connection with id: " + clientId);
        if (requestData != null) {
            Message msg = Message.obtain(null, MESSAGE_REQUEST);
            Bundle data = new Bundle();
            data.putString(KEY_CLIENT_ID, clientId);
            data.putString(KEY_DATA_REQUEST, requestData);
            data.putString(KEY_DATA_SENDER, componentName.flattenToString());
            msg.setData(data);
            msg.replyTo = new Messenger(incomingHandler);
            try {
                outgoingMessenger.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send message", e);
            }
        }
    }
}
