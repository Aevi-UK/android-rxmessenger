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

import android.os.Handler;
import android.os.Looper;

import com.aevi.android.rxmessenger.ChannelServer;
import com.aevi.android.rxmessenger.MessageException;

import java.util.HashSet;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Base handler that can be used as a base class for {@link ChannelServer} implementations
 */
public abstract class BaseChannelServer implements ChannelServer {

    private static final String TAG = BaseChannelServer.class.getSimpleName();

    private final Set<ClientListener> listeners;
    private final BehaviorSubject<String> clientMessages;

    BaseChannelServer() {
        listeners = new HashSet<>();
        clientMessages = BehaviorSubject.create();
    }

    @Override
    public void binderDied() {
        disposeClient();
    }

    @Override
    public String getLastMessageBlocking() {
        return clientMessages.blockingLatest().iterator().next();
    }

    protected void notifyMessage(String message) {
        clientMessages.onNext(message);
    }

    @Override
    public void addClientListener(ClientListener clientListener) {
        listeners.add(clientListener);
    }

    @Override
    public void removeClientListener(ClientListener clientListener) {
        listeners.remove(clientListener);
    }

    @Override
    public boolean send(String message) {
        return false;
    }

    @Override
    public boolean sendEndStream() {
        return false;
    }

    @Override
    public boolean send(MessageException e) {
        return false;
    }

    @Override
    public Observable<String> subscribeToMessages() {
        return clientMessages;
    }

    @Override
    public void disposeClient() {
        clientMessages.onComplete();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (ClientListener listener : listeners) {
                    listener.onClientDispose();
                }
            }
        });
    }

    @Override
    public void closeClient() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (ClientListener listener : listeners) {
                    listener.onClientClosed();
                }
            }
        });
    }
}
