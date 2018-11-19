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

import android.content.Context;
import android.os.Message;
import android.util.Log;

import com.aevi.android.rxmessenger.ChannelServer;
import com.aevi.android.rxmessenger.MessageException;
import com.aevi.android.rxmessenger.model.ConnectionParams;
import com.aevi.android.rxmessenger.service.websocket.WebSocketConnection;
import com.aevi.android.rxmessenger.service.websocket.WebSocketServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;

import io.reactivex.CompletableObserver;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_REQUEST;

/**
 * An websocket implementation of an {@link ChannelServer}. Will automatically fall back to messenger comms if the websocket fails.
 * <p>
 * The websocket will be setup on the first available port found in the range 4001-5999
 * </p>
 */
public class WebSocketChannelServer extends MessengerChannelServer {

    private static final String TAG = WebSocketChannelServer.class.getSimpleName();

    public static final String CONNECT_PLEASE = "connect";

    private WebSocketServer webSocketServer;
    private WebSocketConnection webSocketConnection;
    private Gson gson = new GsonBuilder().create();

    private PublishSubject<String> sendMessageQueue;

    private final Context context;

    private boolean disconnectedWithEndStreamCall = false;

    WebSocketChannelServer(Context context, String serviceComponentName, String clientPackageName) {
        super(serviceComponentName, clientPackageName);
        this.context = context;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_REQUEST:
                if (msg.replyTo != null) {
                    replyTo = msg.replyTo;
                }
                startServer();
                break;
            default:
                super.handleMessage(msg);
        }
    }

    private void startServer() {
        setupSendQueue();
        setupWebServer();
    }

    private void setupWebServer() {
        webSocketServer = createWebSocketServer();
        // start web socket server here and send message to client containing connection details
        webSocketServer.startServer().doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable disposable) throws Exception {
                ConnectionParams connectionParams = new ConnectionParams(webSocketServer.getHostname(), webSocketServer.getPort());
                if (!WebSocketChannelServer.super.send(gson.toJson(connectionParams))) {
                    Log.d(TAG, "Failed to send connection details to client");
                }
            }
        }).observeOn(getSendScheduler()).subscribe(new Consumer<WebSocketConnection>() {
            @Override
            public void accept(WebSocketConnection webSocketConnection) throws Exception {
                Log.d(TAG, "Websocket server started");
                WebSocketChannelServer.this.webSocketConnection = webSocketConnection;
                subscribeToWebSocketMessages(webSocketConnection);
                handleWebSocketDisconnect(webSocketConnection);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                send(new MessageException("websocketError", "Unable to setup websocket server: " + throwable.getMessage()));
            }
        });
    }

    private void setupSendQueue() {
        if (sendMessageQueue == null || sendMessageQueue.hasComplete()) {
            sendMessageQueue = PublishSubject.create();
        }

        sendMessageQueue.observeOn(getSendScheduler()).doOnComplete(new Action() {
            @Override
            public void run() throws Exception {
                finishAndCleanUp();
            }
        }).subscribe(new Consumer<String>() {
            @Override
            public void accept(String message) throws Exception {
                try {
                    if (webSocketConnection != null && webSocketConnection.isConnected()) {
                        webSocketConnection.send(message);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to send message via websocket", e);
                }
            }
        });
    }

    private void finishAndCleanUp() {
        if (webSocketConnection != null) {
            webSocketConnection.disconnect();
        }
    }

    protected Scheduler getSendScheduler() {
        return Schedulers.io();
    }

    protected WebSocketServer createWebSocketServer() {
        return WebSocketServer.create(context);
    }

    private void handleWebSocketDisconnect(WebSocketConnection webSocketConnection) {
        webSocketConnection.onDisconnected().subscribe(new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onComplete() {
                Log.d(TAG, "Websocket disconnected");
                disconnected();
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Websocket error", e);
                disconnected();
            }

            private void disconnected() {
                sendMessageQueue.onComplete();
                if (webSocketServer != null) {
                    webSocketServer.stopServer();
                    webSocketServer = null;
                }

                if (disconnectedWithEndStreamCall) {
                    sendEndStreamBelow();
                }
                disposeClient();
            }
        });
    }

    private void sendEndStreamBelow() {
        super.sendEndStream();
    }

    @Override
    protected void notifyMessage(String message) {
        if (!message.equals(CONNECT_PLEASE)) {
            super.notifyMessage(message);
        }
    }

    private void subscribeToWebSocketMessages(WebSocketConnection webSocketConnection) {
        webSocketConnection.receiveMessages().subscribe(new Consumer<String>() {
            @Override
            public void accept(String message) throws Exception {
                notifyMessage(message);
            }
        });
    }


    @Override
    public boolean send(final String message) {
        if (webSocketConnection != null && webSocketConnection.isConnected()) {
            // normal message sends go over web socket channel
            sendMessageQueue.onNext(message);
            return true;
        } else {
            // fallback to messenger
            return super.send(message);
        }
    }

    @Override
    public boolean sendEndStream() {
        sendMessageQueue.onComplete();
        disconnectedWithEndStreamCall = true;
        return true;
    }
}
