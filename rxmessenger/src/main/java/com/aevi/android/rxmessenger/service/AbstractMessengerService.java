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

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;

import com.aevi.android.rxmessenger.MessageException;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.aevi.android.rxmessenger.MessageConstants.*;

/**
 * Base class for a messenger service that can receive requests and send back responses and errors.
 */
public abstract class AbstractMessengerService extends Service {

    private static final String TAG = AbstractMessengerService.class.getSimpleName();

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

    private void handleIncomingAction(Message msg) {
        Bundle data = msg.getData();
        if (data.containsKey(KEY_DATA_REQUEST)) {
            String clientId = data.getString(KEY_CLIENT_ID, UUID.randomUUID().toString());
            String requestJson = data.getString(KEY_DATA_REQUEST);
            try {
                if (requestJson != null) {
                    Log.d(TAG, "Received valid message from client: " + clientId);
                    if (msg.replyTo != null) {
                        clientMap.put(clientId, msg.replyTo);
                    }
                    String callingPackage = getCallingPackage(msg);
                    handleRequest(clientId, requestJson, callingPackage);
                } else {
                    Log.e(TAG, "Invalid message data");
                }
            } catch (Exception e) {
                Log.e(TAG, "Invalid data", e);
            }
        }
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

    /**
     * To be implemented by the subclass to handle the request.
     *
     * @param clientId    The id of the client. Should be used to return messages back to the originating client
     * @param requestData Any request data to be sent back
     * @param packageName The packageName of the calling client. Can be used to verify permissions (if required)
     */
    protected abstract void handleRequest(String clientId, String requestData, String packageName);

    private Message createMessage(MessageException error) {
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, error.toJson());
        return createMessage(b, MESSAGE_ERROR, false);
    }

    private Message createMessage(String senddata, String dataKey, int what, boolean withReply) {
        Bundle b = new Bundle();
        b.putString(dataKey, senddata);
        return createMessage(b, what, withReply);
    }

    private Message createMessage(Bundle b, int what, boolean withReply) {
        if (b == null) {
            b = new Bundle();
        }
        b.putString(KEY_DATA_SENDER, new ComponentName(getPackageName(), getClass().getName()).flattenToString());
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

    /**
     * Use to send a normal response to a client
     *
     * @param clientId The id of the client
     * @param response The response data to send
     * @return True if the client is still present and will have received the message
     */
    public boolean sendMessageToClient(String clientId, String response) {
        boolean sent = false;
        if (clientId != null && response != null) {
            sent = send(clientId, createMessage(response, KEY_DATA_RESPONSE, MESSAGE_RESPONSE, false));
        }
        return sent;
    }

    /**
     * Use to send to the client that this message stream is done
     *
     * @param clientId The id of the client
     * @return True if the client is still present and will have received the message
     */
    public boolean sendEndStreamMessageToClient(String clientId) {
        boolean sent = false;
        if (clientId != null) {
            sent = send(clientId, createMessage(null, MESSAGE_END_STREAM, false));
            // we are done with this client so clean up
            clientMap.remove(clientId);
        }
        return sent;
    }

    /**
     * Use to send an error message to the client
     *
     * @param clientId The id of the client
     * @param code     An error code to send
     * @param message  An error message to send
     * @return True if the client is still present and will have received the message
     */
    public boolean sendErrorMessageToClient(String clientId, String code, String message) {
        boolean sent = false;
        if (clientId != null) {
            sent = send(clientId, createMessage(new MessageException(code, message)));
            // we are done with this client so clean up
            clientMap.remove(clientId);
        }
        return sent;
    }

    private boolean send(String clientId, Message message) {
        Messenger target = clientMap.get(clientId);
        if (target != null) {
            try {
                target.send(message);
                return true;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send reply to client", e);
                // client has gone away
                clientMap.remove(clientId);
            }
        }
        return false;
    }
}
