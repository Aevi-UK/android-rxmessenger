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
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

import static com.aevi.android.rxmessenger.MessageConstants.KEY_CHANNEL_TYPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CLIENT_ID;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_REQUEST;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_SENDER;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_REQUEST;

class MessengerConnection implements ServiceConnection {

    private static final String TAG = MessengerConnection.class.getSimpleName();

    private final IncomingHandler incomingHandler;
    private final String clientId;
    private final String channelType;
    private final BehaviorSubject<MessengerConnection> bindSubject = BehaviorSubject.create();

    private Messenger outgoingMessenger;
    private ComponentName componentName;
    private boolean bound = false;

    MessengerConnection(IncomingHandler incomingHandler, String clientId, String channelType) {
        this.incomingHandler = incomingHandler;
        this.clientId = clientId;
        this.channelType = channelType;
        Log.d(TAG, "Created connection with id: " + clientId);
    }

    void updateCallbackEmitter(Subject<String> callbackEmitter) {
        incomingHandler.updateCallbackEmitter(callbackEmitter);
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

        Subject<String> callbackEmitter = incomingHandler.getCallbackEmitter();
        if (callbackEmitter != null) {
            callbackEmitter.onComplete();
        }
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
            data.putString(KEY_CHANNEL_TYPE, channelType);
            msg.setData(data);
            msg.replyTo = new Messenger(incomingHandler);
            doSend(msg);
        }
    }

    private void doSend(Message msg) {
        try {
            outgoingMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send message to service", e);
            bindSubject.onError(e);
        }
    }

}
