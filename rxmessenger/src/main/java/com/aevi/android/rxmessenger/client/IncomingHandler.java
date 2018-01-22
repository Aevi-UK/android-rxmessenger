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

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.aevi.android.rxmessenger.MessageException;

import java.lang.ref.WeakReference;

import io.reactivex.subjects.Subject;

import static com.aevi.android.rxmessenger.MessageConstants.*;

class IncomingHandler extends Handler {

    private final WeakReference<ObservableMessengerClient> serviceRef;
    private final Subject<String> callbackEmitter;

    IncomingHandler(ObservableMessengerClient service, Subject<String> callbackEmitter) {
        super(Looper.getMainLooper());
        serviceRef = new WeakReference<>(service);
        this.callbackEmitter = callbackEmitter;
    }

    @Override
    public void handleMessage(Message msg) {
        ObservableMessengerClient client = serviceRef.get();
        if (client != null) {
            Bundle data = msg.getData();
            if (data != null && callbackEmitter.hasObservers()) {
                String sender = data.getString(KEY_DATA_SENDER);
                switch (msg.what) {
                    case MESSAGE_RESPONSE:
                        if (data.containsKey(KEY_DATA_RESPONSE)) {
                            String json = data.getString(KEY_DATA_RESPONSE);
                            client.handleMessage(json, sender, callbackEmitter);
                        }
                        break;
                    case MESSAGE_END_STREAM:
                        callbackEmitter.onComplete();
                        break;
                    case MESSAGE_ERROR:
                        if (data.containsKey(KEY_DATA_RESPONSE)) {
                            String json = data.getString(KEY_DATA_RESPONSE);
                            MessageException response = MessageException.fromJson(json);
                            callbackEmitter.onError(response);
                            break;
                        }
                        // else fall through
                    default:
                        MessageException exception = new MessageException("Message error", "Unknown message type");
                        callbackEmitter.onError(exception);
                        break;
                }
            }
        }
    }
}
