package com.aevi.android.rxmessenger;

import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.List;

@Implements(Messenger.class)
public class MockShadowMessenger {

    private Handler handler;

    private static List<Message> messages = new ArrayList<>();

    public void __constructor__(Handler handler) {
        this.handler = handler;
    }

    @Implementation
    public void send(Message message) throws RemoteException {
        if (handler != null) {
            message.setTarget(handler);
            message.sendToTarget();
        }
        messages.add(message);
    }

    public static List<Message> getMessages() {
        return messages;
    }

    public static void clearMessages() {
        messages.clear();
    }
}
