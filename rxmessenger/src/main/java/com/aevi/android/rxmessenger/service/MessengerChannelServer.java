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
package com.aevi.android.rxmessenger.service;

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.aevi.android.rxmessenger.ChannelServer;
import com.aevi.android.rxmessenger.MessageException;

import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_REQUEST;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_RESPONSE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_SENDER;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_END_STREAM;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_ERROR;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_RESPONSE;

/**
 * An Android {@link Messenger} implementation of an {@link ChannelServer}
 */
public class MessengerChannelServer extends BaseChannelServer {

    private static final String TAG = MessengerChannelServer.class.getSimpleName();

    protected Messenger replyTo;

    private final String serviceComponentName;

    MessengerChannelServer(String serviceComponentName) {
        this.serviceComponentName = serviceComponentName;
    }

    @Override
    public void handleMessage(Message msg) {
        Bundle data = msg.getData();
        if (data != null && data.containsKey(KEY_DATA_REQUEST)) {
            String requestJson = data.getString(KEY_DATA_REQUEST);

            try {
                if (requestJson != null) {
                    Log.d(TAG, "Received valid message from client: " + requestJson);
                    if (msg.replyTo != null) {
                        replyTo = msg.replyTo;
                    }

                    notifyMessage(requestJson);
                } else {
                    Log.e(TAG, "Invalid message data");
                }
            } catch (Exception e) {
                Log.e(TAG, "Invalid data", e);
            }
        }
    }

    @Override
    public void clientDispose() {
        Log.d(TAG, "Client dispose: " + serviceComponentName);
        super.clientDispose();
    }

    @Override
    public boolean send(MessageException error) {
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, error.toJson());
        Message message = createMessage(b, MESSAGE_ERROR);
        return send(message);
    }

    @Override
    public boolean send(String senddata) {
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, senddata);
        Message message = createMessage(b, MESSAGE_RESPONSE);
        return send(message);
    }

    @Override
    public boolean sendEndStream() {
        Message message = createMessage(null, MESSAGE_END_STREAM);
        clientClose();
        return send(message);
    }

    private boolean send(Message message) {
        if (replyTo != null) {
            try {
                replyTo.send(message);
                return true;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send reply to client", e);
                binderDied();
            }
        }
        return false;
    }

    private Message createMessage(Bundle b, int what) {
        if (b == null) {
            b = new Bundle();
        }
        b.putString(KEY_DATA_SENDER, serviceComponentName);
        Message msg = Message.obtain(null, what);
        msg.setData(b);
        return msg;
    }
}

