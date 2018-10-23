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
package com.aevi.android.rxmessenger.client.websocket;

import android.util.Log;

import com.aevi.android.rxmessenger.model.ConnectionParams;

import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

/**
 * For internal use only
 */
public class OkWebSocketClient {

    private static final String TAG = OkWebSocketClient.class.getSimpleName();

    private static final int CODE_CLOSE = 1000;

    private final ConnectionParams connectionParams;
    private WebSocket webSocket;
    private OkWebSocketListener listener;

    public OkWebSocketClient(ConnectionParams connectionParams) {
        this.connectionParams = connectionParams;
    }

    public Completable doConnect(final int timeoutMs) {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                String hostAddress = connectionParams.getHostAddress() == null ? "0.0.0.0" : connectionParams.getHostAddress();
                int port = connectionParams.getPort();
                Log.d(TAG, String.format("Connecting to %s:%d, with timeout %d", hostAddress, port, timeoutMs));
                OkHttpClient client = new OkHttpClient.Builder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS).build();
                Request request = new Request.Builder().url("ws://" + hostAddress + ":" + port).build();
                listener = new OkWebSocketListener(emitter);
                webSocket = client.newWebSocket(request, listener);
            }
        });
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(CODE_CLOSE, "Disconnecting");
            webSocket = null;
        }
    }

    public void sendMessage(String message) {
        webSocket.send(message);
    }

    public void updateCallbackEmitter(PublishSubject<String> responseEmitter) {
        listener.updateCallbackEmitter(responseEmitter);
    }
}
