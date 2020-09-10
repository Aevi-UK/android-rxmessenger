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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;

import com.aevi.android.rxmessenger.ChannelServer;
import com.aevi.android.rxmessenger.service.pipe.Pipe;

import java.io.IOException;

import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_PIPE;
import static com.aevi.android.rxmessenger.service.WebSocketChannelServer.CLOSE_MESSAGE;
import static com.aevi.android.rxmessenger.service.WebSocketChannelServer.CONNECT_PLEASE;

/**
 * An Android {@link Messenger} implementation of an {@link ChannelServer}
 */
public class PipeChannelServer extends BaseChannelServer {

    private Pipe pipe;
    private Subject<String> callback;
    private MessengerChannelServer messenger;
    private String clientPackageName;

    PipeChannelServer(String serviceComponentName, String clientPackageName) {
        super();
        this.clientPackageName = clientPackageName;
        messenger = new MessengerChannelServer(serviceComponentName, clientPackageName);
        messenger.subscribeToMessages().subscribe(new Consumer<String>() {
            @Override
            public void accept(String message) throws Exception {
                if (CONNECT_PLEASE.equals(message)) {
                    try {
                        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createSocketPair();
                        setupPipe(pair[0]);
                        sendClientSetup(pair[1]);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    messenger.disposeClient();
                }
            }
        });
    }

    @Override
    public String getClientPackageName() {
        return clientPackageName;
    }

    @Override
    public boolean send(String data) {
        if (pipe != null) {
            return pipe.write(data);
        }
        return super.send(data);
    }

    @Override
    protected void notifyMessage(String message) {
        super.notifyMessage(message);
    }

    @SuppressLint("CheckResult")
    private void setupPipe(ParcelFileDescriptor descriptor) {
        callback = PublishSubject.create();
        callback.subscribe(new Consumer<String>() {
            @Override
            public void accept(String message) throws Exception {
                notifyMessage(message);
            }
        });
        pipe = new Pipe(descriptor, callback);
        pipe.run();
    }

    @Override
    public boolean sendEndStream() {
        return send(CLOSE_MESSAGE);
    }

    private void sendClientSetup(ParcelFileDescriptor descriptor) {
        Bundle b = new Bundle();
        b.putParcelable(KEY_DATA_PIPE, descriptor);
        messenger.send(b);
    }

    @Override
    public void closeClient() {
        try {
            pipe.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        messenger.disposeClient();
    }

    @Override
    public void handleMessage(Message msg) {
        messenger.handleMessage(msg);
    }
}

