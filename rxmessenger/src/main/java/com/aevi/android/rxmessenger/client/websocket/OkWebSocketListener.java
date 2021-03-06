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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.reactivex.CompletableEmitter;
import io.reactivex.subjects.PublishSubject;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import static com.aevi.android.rxmessenger.service.WebSocketChannelServer.CLOSE_MESSAGE;

/**
 * For internal use only
 */
public class OkWebSocketListener extends WebSocketListener {

    private static final String TAG = OkWebSocketListener.class.getSimpleName();

    private final CompletableEmitter emitter;
    private final OkWebSocketClient okWebSocketClient;
    private PublishSubject<String> responseEmitter;

    OkWebSocketListener(OkWebSocketClient okWebSocketClient, CompletableEmitter emitter) {
        this.emitter = emitter;
        this.okWebSocketClient = okWebSocketClient;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        emitter.onComplete();
    }

    @Override
    public void onMessage(WebSocket webSocket, final String text) {
        if (text != null && !text.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (CLOSE_MESSAGE.equals(text)) {
                        responseEmitter.onComplete();
                        okWebSocketClient.close();
                    } else {
                        if (responseEmitter != null) {
                            responseEmitter.onNext(text);
                        } else {
                            Log.d(TAG, "Receieved message but no response emitter to pass it to");
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "Websocket closed");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (responseEmitter != null) {
                    responseEmitter.onComplete();
                }
            }
        });
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "Websocket failure: " + t.getMessage());
    }

    void updateCallbackEmitter(PublishSubject<String> responseEmitter) {
        this.responseEmitter = responseEmitter;
    }
}
