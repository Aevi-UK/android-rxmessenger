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

    private Map<String, Messenger> clientMap = new HashMap<>();

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

    public void sendMessageToClient(String requestId, P response) {
        if (requestId != null && response != null) {
            send(requestId, createMessage(response, KEY_DATA_RESPONSE, MESSAGE_RESPONSE, false));
        }
    }

    public void sendEndStreamMessageToClient(String requestId) {
        if (requestId != null) {
            send(requestId, createMessage(null, MESSAGE_END_STREAM, false));
        }
    }

    public void sendErrorMessageToClient(String requestId, String code, String message) {
        if (requestId != null) {
            send(requestId, createMessage(new MessageException(code, message)));
        }
    }

    private void send(String requestId, Message message) {
        Messenger target = clientMap.get(requestId);
        if (target != null) {
            try {
                target.send(message);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send reply to client", e);
            }
        }
    }
}
