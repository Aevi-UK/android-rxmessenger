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
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

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

    private final Context context;

    private boolean fromEndStream = false;

    WebSocketChannelServer(Context context, String serviceComponentName) {
        super(serviceComponentName);
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
        webSocketServer = WebSocketServer.create(context);
        // start web socket server here and send message to client containing connection details
        webSocketServer.startServer().subscribe(new Consumer<WebSocketConnection>() {
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

        ConnectionParams connectionParams = new ConnectionParams(webSocketServer.getHostname(), webSocketServer.getPort());
        if (!super.send(gson.toJson(connectionParams))) {
            Log.d(TAG, "Failed to send connection details to client");
        }
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
                if (webSocketServer != null) {
                    webSocketServer.stopServer();
                    webSocketServer = null;
                }

                if (fromEndStream) {
                    sendEndStreamBelow();
                }
                clientDispose();
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
    public boolean send(String message) {
        try {
            if (webSocketConnection != null && webSocketConnection.isConnected()) {
                // normal message sends go over web socket channel
                webSocketConnection.send(message);
                return true;
            } else {
                // fallback to messenger
                return super.send(message);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to send message via websocket", e);
        }
        return false;
    }

    @Override
    public boolean sendEndStream() {
        if (webSocketConnection != null) {
            webSocketConnection.disconnect();
            fromEndStream = true;
            return true;
        }
        return false;
    }
}
