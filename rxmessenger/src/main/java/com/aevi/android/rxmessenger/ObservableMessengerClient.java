package com.aevi.android.rxmessenger;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;

public class ObservableMessengerClient<Q extends Sendable, P extends Sendable> {

    private static final String TAG = ObservableMessengerClient.class.getSimpleName();

    private final Context context;
    private final Class<P> responseType;
    private final OnHandleMessageCallback<P> onHandleMessageCallback;

    protected static class MessengerConnection<Q extends Sendable, P extends Sendable> implements ServiceConnection {

        final ObservableMessengerClient baseMessengerClient;
        Messenger outgoingMessenger;
        ComponentName componentName;
        boolean bound = false;
        Class<P> responseType;
        BehaviorSubject<MessengerConnection<Q, P>> bindSubject = BehaviorSubject.create();

        MessengerConnection(ObservableMessengerClient baseMessengerClient, Class<P> responseType) {
            this.baseMessengerClient = baseMessengerClient;
            this.responseType = responseType;
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

        Observable<MessengerConnection<Q, P>> getConnectedObservable() {
            return bindSubject;
        }

        boolean isBound() {
            return bound;
        }

        void sendMessage(Q request, ObservableEmitter callbackEmitter) {
            if (request != null) {
                Message msg = Message.obtain(null, AbstractMessengerService.MESSAGE_REQUEST);
                Bundle data = new Bundle();
                data.putString(AbstractMessengerService.KEY_DATA_REQUEST, request.toJson());
                data.putString(AbstractMessengerService.DATA_SENDER, componentName.flattenToString());
                msg.setData(data);
                msg.replyTo = new Messenger(new IncomingHandler(baseMessengerClient, callbackEmitter, responseType));
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

    private static class IncomingHandler<P extends Sendable> extends Handler {

        private final WeakReference<ObservableMessengerClient> serviceRef;
        private ObservableEmitter<P> callbackEmitter;
        private Class<P> responseType;

        IncomingHandler(ObservableMessengerClient service, ObservableEmitter<P> callbackEmitter, Class<P> responseType) {
            serviceRef = new WeakReference<>(service);
            this.callbackEmitter = callbackEmitter;
            this.responseType = responseType;
        }

        @Override
        public void handleMessage(Message msg) {
            ObservableMessengerClient client = serviceRef.get();
            if (client != null) {
                Bundle data = msg.getData();
                if (data != null) {
                    String sender = data.getString(AbstractMessengerService.DATA_SENDER);
                    switch (msg.what) {
                        case AbstractMessengerService.MESSAGE_RESPONSE:
                            if (data.containsKey(AbstractMessengerService.KEY_DATA_RESPONSE)) {
                                String json = data.getString(AbstractMessengerService.KEY_DATA_RESPONSE);
                                P response = JsonConverter.deserialize(json, responseType);
                                client.handleMessage(response, sender, callbackEmitter);
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

    public ObservableMessengerClient(Context context, Class<P> responseType) {
        this(context, responseType, null);
    }

    public ObservableMessengerClient(Context context, Class<P> responseType, OnHandleMessageCallback<P> onHandleMessageCallback) {
        this.context = context;
        this.responseType = responseType;
        this.onHandleMessageCallback = onHandleMessageCallback;
    }

    public interface OnHandleMessageCallback<P> {

        void handleMessage(P data, String sender, ObservableEmitter<P> callbackEmitter);
    }

    /**
     * Default handler if just need to send message back to callback
     *
     * Override if different/extra functionality is required in implementation of this base class
     */
    protected void handleMessage(P data, String sender, ObservableEmitter<P> callbackEmitter) {
        if (onHandleMessageCallback == null) {
            callbackEmitter.onNext(data);
        } else {
            onHandleMessageCallback.handleMessage(data, sender, callbackEmitter);
        }
    }

    public Observable<P> createObservableForServiceIntent(final Intent intent, final Q request) {
        return Observable.create(new ObservableOnSubscribe<P>() {
            @Override
            public void subscribe(@NonNull final ObservableEmitter<P> emitter) throws Exception {
                bindToService(intent).subscribe(new Consumer<MessengerConnection<Q, P>>() {
                    @Override
                    public void accept(MessengerConnection<Q, P> messengerConnection) throws Exception {
                        if (messengerConnection.isBound()) {
                            messengerConnection.sendMessage(request, emitter);
                            messengerConnection.shutDown();
                        } else {
                            // FIXME - use custom exception
                            emitter.onError(new RuntimeException("Unable to bind to payment app service"));
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        Log.e(TAG, "Failed to bind to service", throwable);
                        emitter.onError(new RuntimeException("Failed to find service for binding"));
                    }
                });
            }
        });
    }

    private Observable<MessengerConnection<Q, P>> bindToService(Intent serviceIntent) {
        MessengerConnection<Q, P> messengerConnection = new MessengerConnection<>(this, responseType);
        context.bindService(serviceIntent, messengerConnection, Context.BIND_AUTO_CREATE);
        return messengerConnection.getConnectedObservable();
    }
}
