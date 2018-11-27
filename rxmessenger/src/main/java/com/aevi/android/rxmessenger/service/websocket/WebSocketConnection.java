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
package com.aevi.android.rxmessenger.service.websocket;

import android.util.Log;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.subjects.CompletableSubject;
import io.reactivex.subjects.PublishSubject;

import static fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode.NormalClosure;

/**
 * Internal class used to handle a websocket connection
 */
public class WebSocketConnection extends NanoWSD.WebSocket {

    private static final String TAG = WebSocketConnection.class.getSimpleName();
    private CompletableSubject connectSubject = CompletableSubject.create();
    private PublishSubject<String> responseSubject = PublishSubject.create();
    private CompletableSubject disconnectedSubject = CompletableSubject.create();

    WebSocketConnection(NanoHTTPD.IHTTPSession handshakeRequest) {
        super(handshakeRequest);
    }

    public boolean isConnected() {
        return isOpen();
    }

    Completable onConnected() {
        return connectSubject;
    }

    public Completable onDisconnected() {
        return disconnectedSubject;
    }

    public void disconnect() {
        try {
            close(NormalClosure, "Disconnect request", false);
        } catch (IOException e) {
            Log.e(TAG, "Failed to disconnect: " + e.getMessage());
        }
    }

    public Observable<String> receiveMessages() {
        return responseSubject;
    }

    private void sendMessage(String data) {
        try {
            send(data);
        } catch (IOException e) {
            Log.e(TAG, "Failed to send message", e);
        }
    }

    @Override
    protected void onOpen() {
        Log.d(TAG, "Websocket open");
        connectSubject.onComplete();
    }

    @Override
    protected void onClose(NanoWSD.WebSocketFrame.CloseCode closeCode, String s, boolean b) {
        Log.d(TAG, "Websocket closed: " + closeCode);
        disconnectedSubject.onComplete();
    }

    @Override
    protected void onMessage(NanoWSD.WebSocketFrame webSocketFrame) {
        Log.d(TAG, "Received payload: " + webSocketFrame.getTextPayload());
        if (!webSocketFrame.getTextPayload().isEmpty()) {
            responseSubject.onNext(webSocketFrame.getTextPayload());
        }
    }

    @Override
    protected void onPong(NanoWSD.WebSocketFrame pong) {
        // No-op
    }

    @Override
    protected void onException(IOException e) {
        Log.e(TAG, "Websocket exception", e);
        disconnect();
        disconnectedSubject.onComplete();
    }
}
