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
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.aevi.android.rxmessenger.client.websocket.OkWebSocketClient;
import com.aevi.android.rxmessenger.model.ConnectionParams;
import com.aevi.android.rxmessenger.service.AbstractChannelService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.Callable;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;

import static com.aevi.android.rxmessenger.MessageConstants.*;
import static com.aevi.android.rxmessenger.service.WebSocketChannelServer.CONNECT_PLEASE;

/**
 * Client that sends messages to an {@link AbstractChannelService} and returns an Observable stream of response data from that service.
 *
 * <p>
 * The connection to the service is created via {@link #connect()} or on the first call to {@link #sendMessage(String)}
 * and kept open until {@link #closeConnection()} is called (or the service dies/crashes/etc).
 * </p>
 * <p>
 * On connect a first message will be automatically sent via the standard Android Messenger connection. The response from the server will include
 * websocket connection details and all messaging sent between client and server will use this there after. Should the websocket fail to be setup
 * this client will automatically fall back to using the basic Android Messenger connection.
 * </p>
 * <p>
 * The way a client is identified is based on a client id that is generated for each connection. Once a connection has been created, all messages
 * on the service end will appear to be from the same client, until it is closed. One re-opened, a new client id will be used.
 * </p>
 */
public class ObservableWebSocketClient extends ObservableMessengerClient {

    private static final String TAG = ObservableWebSocketClient.class.getSimpleName();

    private static final int CONNECTION_TIMEOUT = 2000;

    private OkWebSocketClient okWebSocketClient;

    private Gson gson = new GsonBuilder().create();

    public ObservableWebSocketClient(Context context, ComponentName serviceComponentName) {
        super(context, serviceComponentName);
    }

    @NonNull
    protected Intent getServiceIntent(String clientId) {
        Intent intent = super.getServiceIntent(clientId);
        intent.putExtra(KEY_CHANNEL_TYPE, CHANNEL_WEBSOCKET);
        return intent;
    }

    @Override
    protected String getChannelType() {
        return CHANNEL_WEBSOCKET;
    }

    @Override
    public Completable connect() {
        if (isConnected()) {
            return Completable.complete();
        }
        return super.connect().andThen(webSocketSetupCompletable());
    }

    private Completable webSocketSetupCompletable() {
        if (okWebSocketClient != null && okWebSocketClient.isConnected()) {
            return Completable.complete();
        }
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter emitter) throws Exception {
                ObservableWebSocketClient.super.sendMessage(CONNECT_PLEASE).take(1).subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String message) throws Exception {
                        ConnectionParams params = gson.fromJson(message, ConnectionParams.class);
                        okWebSocketClient = getWebSocketClient(params);
                        okWebSocketClient.doConnect(CONNECTION_TIMEOUT).subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onComplete() {
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        emitter.onComplete();
                                    }
                                });
                            }

                            @Override
                            public void onError(Throwable e) {
                                emitter.onError(e);
                            }
                        });
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        emitter.onError(throwable);
                    }
                });
            }
        });
    }

    protected OkWebSocketClient getWebSocketClient(ConnectionParams params) {
        return new OkWebSocketClient(params);
    }

    @Override
    public boolean isConnected() {
        return super.isConnected() && okWebSocketClient != null && okWebSocketClient.isConnected();
    }

    @Override
    public Observable<String> sendMessage(final String message) {
        if (!super.isConnected()) {
            return connectAndSendMessage(message);
        } else {
            if (responseEmitter == null || responseEmitter.hasComplete()) {
                responseEmitter = PublishSubject.create();
            }

            if (okWebSocketClient != null && okWebSocketClient.isConnected()) {
                okWebSocketClient.updateCallbackEmitter(responseEmitter);
                okWebSocketClient.sendMessage(message);
            } else {
                // fallback to Messenger
                super.sendMessage(message);
            }
            return responseEmitter;
        }
    }

    private Observable<String> connectAndSendMessage(final String requestData) {
        return super.connect().andThen(webSocketSetupCompletable()).andThen(Observable.defer(new Callable<ObservableSource<? extends String>>() {
            @Override
            public ObservableSource<? extends String> call() throws Exception {
                return responseEmitter.doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        okWebSocketClient.updateCallbackEmitter(responseEmitter);
                        okWebSocketClient.sendMessage(requestData);
                    }
                });
            }
        }));
    }

    @Override
    public void closeConnection() {
        if (okWebSocketClient != null && okWebSocketClient.isConnected()) {
            okWebSocketClient.close();
        }
        super.closeConnection();
    }
}
