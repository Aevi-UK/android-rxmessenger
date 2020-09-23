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
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import com.aevi.android.rxmessenger.ChannelClient;
import com.aevi.android.rxmessenger.service.AbstractChannelService;
import com.aevi.android.rxmessenger.service.pipe.Pipe;

import java.util.concurrent.Callable;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.CompletableSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import static com.aevi.android.rxmessenger.MessageConstants.CHANNEL_PIPE;
import static com.aevi.android.rxmessenger.MessageConstants.CHANNEL_WEBSOCKET;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CHANNEL_TYPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_PIPE;
import static com.aevi.android.rxmessenger.service.WebSocketChannelServer.CONNECT_PLEASE;

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
public class ObservablePipeClient extends ObservableMessengerClient {

    private Pipe pipe;
    private CompletableSubject setup;

    /**
     * Create an instance with default message handling.
     *
     * @param context              The context to use for binding to the service
     * @param serviceComponentName The component name of the {@link AbstractChannelService} to bind to
     */
    public ObservablePipeClient(Context context, ComponentName serviceComponentName) {
        super(context, serviceComponentName);
    }

    @Override
    void handleData(Bundle data, String sender, Subject<String> callbackEmitter) {
        super.handleData(data, sender, callbackEmitter);
        ParcelFileDescriptor descriptor = data.getParcelable(KEY_DATA_PIPE);
        if (descriptor != null) {
            setupPipe(descriptor);
        }
    }

    private void setupPipe(ParcelFileDescriptor descriptor) {
        pipe = getPipe(descriptor);
        pipe.run();
        setup.onComplete();
    }

    protected Pipe getPipe(ParcelFileDescriptor descriptor) {
        return new Pipe(descriptor, responseEmitter);
    }

    @Override
    public Completable connect() {
        if (pipe != null && pipe.isConnected()) {
            return Completable.complete();
        }
        return super.connect().andThen(pipeSetupCompletable());
    }

    private Completable pipeSetupCompletable() {
        if (pipe != null && pipe.isConnected()) {
            return Completable.complete();
        }

        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter emitter) throws Exception {
                ObservablePipeClient.super.sendMessage(CONNECT_PLEASE).take(1).subscribe();
                setup = CompletableSubject.create();
                setup.subscribe(new Action() {
                    @Override
                    public void run() throws Exception {
                        emitter.onComplete();
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

    @Override
    public Observable<String> sendMessage(final String requestData) {
        if (responseEmitter == null || responseEmitter.hasComplete()) {
            responseEmitter = PublishSubject.create();
            if (pipe != null) {
                pipe.updateCallback(responseEmitter);
            }
        }
        if (pipe == null || !pipe.isConnected()) {
            return connectAndSendMessage(requestData);
        } else {
            if (pipe != null && pipe.isConnected()) {
                pipe.write(requestData);
            } else {
                // Fallback to Messenger
                super.sendMessage(requestData);
            }
            return responseEmitter;
        }
    }

    private Observable<String> connectAndSendMessage(final String requestData) {
        return connect().andThen(Observable.defer(new Callable<ObservableSource<? extends String>>() {
            @Override
            public ObservableSource<? extends String> call() throws Exception {
                pipe.write(requestData);
                return responseEmitter;
            }
        }));
    }

    @Override
    protected String getChannelType() {
        return CHANNEL_PIPE;
    }

    @Override
    public boolean isConnected() {
        return super.isConnected() || pipe != null && pipe.isConnected();
    }

    @NonNull
    protected Intent getServiceIntent(String clientId) {
        Intent intent = super.getServiceIntent(clientId);
        intent.putExtra(KEY_CHANNEL_TYPE, CHANNEL_PIPE);
        return intent;
    }

    @Override
    public void closeConnection() {
        super.closeConnection();
        if (pipe != null) {
            Pipe.safeClose(pipe);
            pipe = null;
        }
        setup = null;
        if (responseEmitter != null) {
            responseEmitter.onComplete();
            responseEmitter = null;
        }
    }
}
