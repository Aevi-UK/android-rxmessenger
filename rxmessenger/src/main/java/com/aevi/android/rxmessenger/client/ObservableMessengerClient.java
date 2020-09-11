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
import android.content.Context;
import android.content.Intent;

import android.util.Log;

import androidx.annotation.NonNull;

import com.aevi.android.rxmessenger.ChannelClient;
import com.aevi.android.rxmessenger.service.AbstractChannelService;

import java.util.UUID;
import java.util.concurrent.Callable;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import static com.aevi.android.rxmessenger.MessageConstants.*;

/**
 * Client that sends requests to an {@link AbstractChannelService} and returns an Observable stream of response data from that service.
 * <p>
 * The connection to the service is created via {@link #connect()} or on the first call to {@link #sendMessage(String)}
 * and kept open until {@link #closeConnection()} is called (or the service dies/crashes/etc).
 * </p>
 * <p>
 * The way a client is identified is based on a client id that is generated for each connection. Once a connection has been created, all messages
 * on the service end will appear to be from the same client, until it is closed. One re-opened, a new client id will be used.
 * </p>
 */
public class ObservableMessengerClient extends BaseChannelClient implements ChannelClient {

    private static final String TAG = ObservableMessengerClient.class.getSimpleName();

    private final OnHandleMessageCallback onHandleMessageCallback;
    PublishSubject<String> responseEmitter;
    private MessengerConnection messengerConnection;

    /**
     * Create an instance with default message handling.
     *
     * @param context              The context to use for binding to the service
     * @param serviceComponentName The component name of the {@link AbstractChannelService} to bind to
     */
    public ObservableMessengerClient(Context context, ComponentName serviceComponentName) {
        this(context, serviceComponentName, null);
    }

    /**
     * Create an instance with custom message handling.
     *
     * @param context                 The context to use for binding to the service
     * @param serviceComponentName    The component name of the {@link AbstractChannelService} to bind to
     * @param onHandleMessageCallback The callback to handle the message received
     */
    public ObservableMessengerClient(Context context, ComponentName serviceComponentName, OnHandleMessageCallback onHandleMessageCallback) {
        super(context, serviceComponentName);
        Log.d(TAG, "Creating client for service: " + serviceComponentName.flattenToShortString());
        this.onHandleMessageCallback = onHandleMessageCallback;
    }

    /**
     * Default handler proxies the message straight to the client.
     */
    void handleMessage(String data, String sender, Subject<String> callbackEmitter) {
        if (onHandleMessageCallback == null) {
            callbackEmitter.onNext(data);
        } else {
            onHandleMessageCallback.handleMessage(data, sender, callbackEmitter);
        }
    }

    /**
     * Returns whether we are connected to the service or not.
     *
     * @return True if connected, false otherwise.
     */
    public boolean isConnected() {
        return messengerConnection != null && messengerConnection.isBound();
    }

    /**
     * Connect to the remote service.
     * <p>
     * The connection will then be kept open until the remote end closes it or {@link #closeConnection()} is called on this instance.
     * </p>
     * Note that {@link #sendMessage(String)} will automatically connect if required to send a message.
     *
     * @return Completable that will complete on success and error on failure
     */
    public Completable connect() {
        if (messengerConnection != null && messengerConnection.isBound()) {
            return Completable.complete();
        }
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter completableEmitter) throws Exception {
                bindToService().subscribe(new Consumer<MessengerConnection>() {
                    @Override
                    public void accept(MessengerConnection messengerConnection) throws Exception {
                        ObservableMessengerClient.this.messengerConnection = messengerConnection;
                        completableEmitter.onComplete();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        completableEmitter.onError(throwable);
                    }
                });
            }
        });
    }

    /**
     * Used to send a message to an {@link AbstractChannelService} implementation and observe the responses from it.
     * <p>
     * This will connect to the service if not already connected when called.
     * </p>
     * The stream returned will only return messages from the point of subscription.
     * <p>
     * NOTE: The messages are only sent once a client is subscribed to the Observable.
     * </p>
     *
     * @param requestData The data to send (usually a serialised JSON object)
     * @return An Observable stream of Strings containing data that the service sends back to this client
     */
    public Observable<String> sendMessage(final String requestData) {
        if (messengerConnection == null || !messengerConnection.isBound()) {
            return connectAndSendMessage(requestData);
        } else {
            // The service may have sent end of stream previously, so for each "round", we then create a new emitter
            if (responseEmitter.hasComplete()) {
                responseEmitter = PublishSubject.create();
                messengerConnection.updateCallbackEmitter(responseEmitter);
            }
            return responseEmitter.doOnSubscribe(new Consumer<Disposable>() {
                @Override
                public void accept(Disposable disposable) throws Exception {
                    messengerConnection.sendMessage(requestData);
                }
            });
        }
    }

    private Observable<String> connectAndSendMessage(final String requestData) {
        return connect().andThen(Observable.defer(new Callable<ObservableSource<? extends String>>() {
            @Override
            public ObservableSource<? extends String> call() throws Exception {
                return responseEmitter.doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        messengerConnection.sendMessage(requestData);
                    }
                });
            }
        }));
    }

    /**
     * Close the connection to the service.
     * <p>
     * This will complete the response stream returned from {@link #sendMessage(String)}.
     * </p>
     * Calling {@link #sendMessage(String)} after this point will create a new connection.
     */
    public void closeConnection() {
        if (messengerConnection != null) {
            Log.d(TAG, "Closing connection with id: " + messengerConnection.getClientId());
            try {
                context.unbindService(messengerConnection);
            } catch (Throwable t) {
                // Ignore
            }
            messengerConnection = null;

            if (responseEmitter != null) {
                responseEmitter.onComplete();
                responseEmitter = null;
            }
        }
    }

    private Observable<MessengerConnection> bindToService() {
        responseEmitter = PublishSubject.create();
        IncomingHandler incomingHandler = new IncomingHandler(this, responseEmitter);
        String clientId = UUID.randomUUID().toString();
        Intent serviceIntent = getServiceIntent(clientId);
        String channelType = getChannelType();
        serviceIntent.putExtra(KEY_CHANNEL_TYPE, channelType);
        MessengerConnection messengerConnection = new MessengerConnection(incomingHandler, clientId, channelType, context.getPackageName());
        boolean canBind = context.bindService(serviceIntent, messengerConnection, Context.BIND_AUTO_CREATE);
        if (canBind) {
            return messengerConnection.getConnectedObservable();
        } else {
            return Observable.error(new NoSuchServiceException(String.format("RxMessenger service %s not found", serviceComponentName)));
        }
    }

    protected String getChannelType() {
        return CHANNEL_MESSENGER;
    }

    @NonNull
    protected Intent getServiceIntent(String clientId) {
        Intent serviceIntent = new Intent();
        serviceIntent.setComponent(serviceComponentName);
        serviceIntent.putExtra(KEY_CLIENT_ID, clientId);
        serviceIntent.putExtra(KEY_DATA_SENDER, context.getPackageName());
        return serviceIntent;
    }

    public interface OnHandleMessageCallback {

        void handleMessage(String data, String sender, Subject<String> callbackEmitter);
    }
}
