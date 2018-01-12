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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

public class ObservableMessengerClient {

    private static final String TAG = ObservableMessengerClient.class.getSimpleName();

    private final Context context;
    private final OnHandleMessageCallback onHandleMessageCallback;
    private MessengerConnection messengerConnection;

    protected static class MessengerConnection implements ServiceConnection {

        final ObservableMessengerClient baseMessengerClient;
        final IncomingHandler incomingHandler;

        Messenger outgoingMessenger;
        ComponentName componentName;
        boolean bound = false;
        BehaviorSubject<MessengerConnection> bindSubject = BehaviorSubject.create();

        MessengerConnection(ObservableMessengerClient baseMessengerClient, IncomingHandler incomingHandler) {
            this.baseMessengerClient = baseMessengerClient;
            this.incomingHandler = incomingHandler;
        }

        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            if (componentName != null) {
                Log.d(ObservableMessengerClient.class.getSimpleName(), "Bound to service - " + componentName.flattenToString());
            }
            this.componentName = componentName;
            outgoingMessenger = new Messenger(binder);
            bound = true;
            bindSubject.onNext(this);
        }

        public void onServiceDisconnected(ComponentName className) {
            if (className != null) {
                Log.d(ObservableMessengerClient.class.getSimpleName(), "Unbound from service - " + className.flattenToString());
            }
            bound = false;
            bindSubject.onComplete();
        }

        Observable<MessengerConnection> getConnectedObservable() {
            return bindSubject;
        }

        boolean isBound() {
            return bound;
        }

        void sendMessage(String requestData) {
            if (requestData != null) {
                Message msg = Message.obtain(null, AbstractMessengerService.MESSAGE_REQUEST);
                Bundle data = new Bundle();
                data.putString(AbstractMessengerService.KEY_DATA_REQUEST, requestData);
                data.putString(AbstractMessengerService.DATA_SENDER, componentName.flattenToString());
                msg.setData(data);
                msg.replyTo = new Messenger(incomingHandler);
                try {
                    outgoingMessenger.send(msg);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to send message", e);
                }
            }
        }

        void shutDown() {
            baseMessengerClient.context.unbindService(this);
            bound = false;
        }
    }

    private static class IncomingHandler extends Handler {

        private final WeakReference<ObservableMessengerClient> serviceRef;
        private Subject<String> callbackEmitter;

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
                    String sender = data.getString(AbstractMessengerService.DATA_SENDER);
                    switch (msg.what) {
                        case AbstractMessengerService.MESSAGE_RESPONSE:
                            if (data.containsKey(AbstractMessengerService.KEY_DATA_RESPONSE)) {
                                String json = data.getString(AbstractMessengerService.KEY_DATA_RESPONSE);
                                client.handleMessage(json, sender, callbackEmitter);
                            }
                            break;
                        case AbstractMessengerService.MESSAGE_END_STREAM:
                            callbackEmitter.onComplete();
                            break;
                        case AbstractMessengerService.MESSAGE_ERROR:
                            if (data.containsKey(AbstractMessengerService.KEY_DATA_RESPONSE)) {
                                String json = data.getString(AbstractMessengerService.KEY_DATA_RESPONSE);
                                MessageException response = MessageException.fromJson(json);
                                callbackEmitter.onError(response);
                            }
                            break;
                    }
                }
            }
        }
    }

    public ObservableMessengerClient(Context context) {
        this(context, null);
    }

    public ObservableMessengerClient(Context context, OnHandleMessageCallback onHandleMessageCallback) {
        this.context = context;
        this.onHandleMessageCallback = onHandleMessageCallback;
    }

    public interface OnHandleMessageCallback {

        void handleMessage(String data, String sender, Subject<String> callbackEmitter);
    }

    /**
     * <p>
     * Default handler if just need to send message back to callback
     * </p>
     * Override if different/extra functionality is required in implementation of this base class
     */
    protected void handleMessage(String data, String sender, Subject<String> callbackEmitter) {
        if (onHandleMessageCallback == null) {
            callbackEmitter.onNext(data);
        } else {
            onHandleMessageCallback.handleMessage(data, sender, callbackEmitter);
        }
    }

    /**
     * Used to send a message to an {@link AbstractMessengerService} implementation and observe the responses from it
     *
     * @param intent      The Intent of the {@link android.app.Service} to call
     * @param requestData The data to send (usually a serialised JSON object)
     * @return An Observable stream of Strings containing data that the service sends back to this client
     */
    public Observable<String> createObservableForServiceIntent(final Intent intent, final String requestData) {
        final BehaviorSubject<String> callbackEmitter = BehaviorSubject.create();
        final IncomingHandler incomingHandler = new IncomingHandler(this, callbackEmitter);

        bindToService(intent, incomingHandler).subscribe(new Consumer<MessengerConnection>() {
            @Override
            public void accept(@NonNull MessengerConnection messengerConnection) throws Exception {
                 ObservableMessengerClient.this.messengerConnection = messengerConnection;
                if (messengerConnection.isBound()) {
                    messengerConnection.sendMessage(requestData);
                } else {
                    // FIXME - use custom exception
                    callbackEmitter.onError(new RuntimeException("Unable to bind to service: " + intent.getAction()));
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                callbackEmitter.onError(throwable);
            }
        });

        return callbackEmitter.doFinally(new Action() {
            @Override
            public void run() throws Exception {
                if (messengerConnection != null) {
                    messengerConnection.shutDown();
                    messengerConnection = null;
                }
            }
        });
    }

    private Observable<MessengerConnection> bindToService(Intent serviceIntent, IncomingHandler incomingHandler) {
        MessengerConnection messengerConnection = new MessengerConnection(this, incomingHandler);
        context.bindService(serviceIntent, messengerConnection, Context.BIND_AUTO_CREATE);
        return messengerConnection.getConnectedObservable();
    }
}
