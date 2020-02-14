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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
    private final String serverPackageName;
    private WebSocket webSocket;
    private OkWebSocketListener listener;

    public OkWebSocketClient(ConnectionParams connectionParams, String serverPackageName) {
        this.connectionParams = connectionParams;
        this.serverPackageName = serverPackageName;
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

        // EVERY server has a different and unique self signed certificate.
        // We are assuming (trusting) that the end point server is who we think it is by checking the CN of the certificate matches
        // the package name we have requested to connect to. This of course could be spoofed as there is no verifying certifcate authority chain
        // as this would require a CA private key to be stored/accessible to your application.
        // We are using websockets over TLS here ONLY to ensure data is encrypted in transit
        final TrustManager[] trustAllCerts = new TrustManager[]{new CertMatchingTrustManager(serverPackageName)};

        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }

    private static class CertMatchingTrustManager implements X509TrustManager {

        private String serverPackageName;

        public CertMatchingTrustManager(String serverPackageName) {
            this.serverPackageName = serverPackageName;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            for (X509Certificate certificate : x509Certificates) {
                String toMatch = certificate.getSubjectDN().getName();
                if (toMatch.contains("CN=" + serverPackageName)) {
                    return;
                }
            }
            throw new CertificateException("Attempt to connect to untrusted/incorrect rx-messenger server");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
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
