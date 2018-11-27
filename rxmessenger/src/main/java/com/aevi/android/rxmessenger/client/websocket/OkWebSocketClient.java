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

import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

import static com.aevi.android.rxmessenger.model.KeystoreCredentials.KEYSTORE_FILENAME;
import static com.aevi.android.rxmessenger.model.KeystoreCredentials.KEYSTORE_PASS;

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

    @SuppressWarnings("deprecation")
    public Completable doConnect(final int timeoutMs) {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(CompletableEmitter emitter) throws Exception {
                String hostAddress = connectionParams.getHostAddress() == null ? "0.0.0.0" : connectionParams.getHostAddress();
                int port = connectionParams.getPort();
                Log.d(TAG, String.format("Connecting to %s:%d, with timeout %d", hostAddress, port, timeoutMs));
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .sslSocketFactory(makeSecure())
                        .hostnameVerifier(getOpenVerifier())
                        .build();
                Request request = new Request.Builder().url("wss://" + hostAddress + ":" + port).build();
                listener = new OkWebSocketListener(OkWebSocketClient.this, emitter);
                webSocket = client.newWebSocket(request, listener);
            }
        });
    }

    private HostnameVerifier getOpenVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
    }

    private SSLSocketFactory makeSecure() throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream keystoreStream = OkWebSocketClient.class.getResourceAsStream(KEYSTORE_FILENAME);
        keystore.load(keystoreStream, KEYSTORE_PASS);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keystore);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keystore, KEYSTORE_PASS);
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
        return sslContext.getSocketFactory();
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
