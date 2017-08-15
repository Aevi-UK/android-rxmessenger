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
package com.aevi.android.rxmessenger;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMessengerService<Q extends Sendable, P extends Sendable> extends Service {

    private static final String TAG = AbstractMessengerService.class.getSimpleName();

    protected static final int MESSAGE_REQUEST = 1;
    protected static final int MESSAGE_RESPONSE = 4;
    protected static final int MESSAGE_END_STREAM = 8;
    protected static final int MESSAGE_ERROR = 16;

    protected static final String KEY_DATA_REQUEST = "dataRequest";
    protected static final String KEY_DATA_RESPONSE = "dataResponse";

    protected static final String DATA_SENDER = "sender";

    private final Class<Q> requestType;

    protected Map<String, Messenger> clientMap = new HashMap<>();

    static class IncomingHandler extends Handler {

        private final WeakReference<AbstractMessengerService> serviceRef;

        IncomingHandler(AbstractMessengerService service) {
            serviceRef = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            AbstractMessengerService service = serviceRef.get();
            if (service != null) {
                switch (msg.what) {
                    case MESSAGE_REQUEST:
                        service.handleIncomingAction(msg);
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
        }
    }

    protected final Messenger incomingMessenger = new Messenger(new IncomingHandler(this));

    protected AbstractMessengerService(Class<Q> requestType) {
        this.requestType = requestType;
    }

    private void handleIncomingAction(Message msg) {
        Bundle data = msg.getData();
        if (data.containsKey(KEY_DATA_REQUEST)) {
            String requestJson = data.getString(KEY_DATA_REQUEST);
            try {
                Q object = deserialiseRequestObject(requestJson);
                if (object != null) {
                    if (msg.replyTo != null) {
                        clientMap.put(object.getId(), msg.replyTo);
                    }
                    String callingPackage = getCallingPackage(msg);
                    handleRequest(object, callingPackage);
                } else {
                    Log.e(TAG, "Invalid VAA data: " + requestJson);
                }
            } catch (Exception e) {
                Log.e(TAG, "Invalid data sent to PCS", e);
            }
        }
    }

    protected Q deserialiseRequestObject(String requestJson) {
        return JsonConverter.deserialize(requestJson, requestType);
    }

    private String getCallingPackage(Message msg) {
        String callingPackage = "";
        PackageManager pm = getPackageManager();
        if (pm != null) {
            String[] packages = pm.getPackagesForUid(msg.sendingUid);
            if (packages != null && packages.length >= 1) {
                callingPackage = packages[0];
            }
        }
        return callingPackage;
    }

    protected abstract void handleRequest(Q action, String packageName);

    private Message createMessage(MessageException error) {
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, error.toJson());
        return createMessage(b, MESSAGE_ERROR, false);
    }

    private Message createMessage(P sendable, String dataKey, int what, boolean withReply) {
        Bundle b = new Bundle();
        b.putString(dataKey, sendable.toJson());
        return createMessage(b, what, withReply);
    }

    private Message createMessage(Bundle b, int what, boolean withReply) {
        if (b == null) {
            b = new Bundle();
        }
        b.putString(DATA_SENDER, new ComponentName(getPackageName(), getClass().getName()).flattenToString());
        Message msg = Message.obtain(null, what);
        msg.setData(b);
        if (withReply) {
            msg.replyTo = incomingMessenger;
        }
        return msg;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return incomingMessenger.getBinder();
    }

    public boolean sendMessageToClient(String requestId, P response) {
        boolean sent = false;
        if (requestId != null && response != null) {
            sent = send(requestId, createMessage(response, KEY_DATA_RESPONSE, MESSAGE_RESPONSE, false));
        }
        return sent;
    }

    public boolean sendEndStreamMessageToClient(String requestId) {
        boolean sent = false;
        if (requestId != null) {
            sent = send(requestId, createMessage(null, MESSAGE_END_STREAM, false));
            // we are done with this client so clean up
            clientMap.remove(requestId);
        }
        return sent;
    }

    public boolean sendErrorMessageToClient(String requestId, String code, String message) {
        boolean sent = false;
        if (requestId != null) {
            sent = send(requestId, createMessage(new MessageException(code, message)));
            // we are done with this client so clean up
            clientMap.remove(requestId);
        }
        return sent;
    }

    private boolean send(String requestId, Message message) {
        Messenger target = clientMap.get(requestId);
        if (target != null) {
            try {
                target.send(message);
                return true;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send reply to client", e);
                // client has gone away
                clientMap.remove(requestId);
            }
        }
        return false;
    }
}
