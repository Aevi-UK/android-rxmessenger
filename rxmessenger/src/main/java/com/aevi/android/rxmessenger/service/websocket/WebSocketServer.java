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

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import io.reactivex.CompletableObserver;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

import static android.content.Context.WIFI_SERVICE;

/**
 * Internal class used to create a websocket server that channels can use to send/receive messages
 */
public class WebSocketServer extends NanoWSD {

    private static final String TAG = WebSocketServer.class.getSimpleName();

    private static final int MIN_PORT = 4001;
    private static final int MAX_PORT = 5999;

    private PublishSubject<WebSocketConnection> connectionSubject = PublishSubject.create();

    private String hostname;
    private int port;

    private WebSocketServer(Context context, String hostname, int port) {
        super(hostname, port);
        this.hostname = hostname;
        this.port = port;
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate(context);
            makeSecure(NanoHTTPD.makeSSLSocketFactory(ssc.getKeystore(), ssc.getKeyManagerFactory()), null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to make secure ws server", e);
        }
    }

    public int getPort() {
        return port;
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession ihttpSession) {
        final WebSocketConnection webSocket = new WebSocketConnection(ihttpSession);
        webSocket.onConnected().subscribe(new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onComplete() {
                connectionSubject.onNext(webSocket);
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "openWebsocket error", e);
            }
        });

        return webSocket;
    }

    public Observable<WebSocketConnection> startServer() {
        Log.d(TAG, String.format("Starting web server on: %s:%d", hostname, port));
        try {
            start(0);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
            connectionSubject.onError(new IllegalStateException("Failed to start server"));
        }
        return connectionSubject;
    }

    public void stopServer() {
        Log.d(TAG, "Stopping server");
        stop();
        connectionSubject.onComplete();
    }

    @SuppressWarnings("deprecation")
    public static WebSocketServer create(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return new WebSocketServer(context, ip, nextFreePort(MIN_PORT, MAX_PORT));
    }

    private static int nextFreePort(int from, int to) {
        int port = ThreadLocalRandom.current().nextInt(from, to);
        while (true) {
            if (isLocalPortFree(port)) {
                return port;
            } else {
                port = ThreadLocalRandom.current().nextInt(from, to);
            }
        }
    }

    private static boolean isLocalPortFree(int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
