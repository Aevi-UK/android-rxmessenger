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
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.aevi.android.rxmessenger.ChannelServer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.aevi.android.rxmessenger.MessageConstants.CHANNEL_MESSENGER;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CHANNEL_TYPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CLIENT_ID;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_REQUEST;

/**
 * Base class for an Android service that can be used receive client requests and send back responses and errors over various channels.
 * <p>
 * As with all Android {@link Service} classes this class will keep running until {@link #stopSelf()} is called. It is the responsibility of
 * implementations of this class to ensure the stop method is called at the correct time. Alternatively the  {@link #setStopSelfOnEndOfStream(boolean)}
 * method can be used to set a flag which will automatically stop this service once all {@link com.aevi.android.rxmessenger.ChannelClient} instances
 * have unbound from this service.
 * </p>
 * If you want to support the websocket channel implementation in your application then you must include the following network permissions in your
 * manifest:
 * <code>
 * <uses-permission android:name="android.permission.INTERNET"/>
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
 * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
 * </code>
 */
public abstract class AbstractChannelService extends Service {

    private static final String TAG = AbstractChannelService.class.getSimpleName();

    protected final Map<String, ChannelServer> channelServerMap = new HashMap<>();

    private String serviceName;
    protected IncommingHandler incomingHandler;

    private boolean stopSelfOnEndOfStream;

    static class IncommingHandler extends Handler {

        private final WeakReference<AbstractChannelService> serviceRef;

        IncommingHandler(AbstractChannelService abstractChannelService) {
            serviceRef = new WeakReference<>(abstractChannelService);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REQUEST:
                    Bundle data = msg.getData();
                    String msgClientId = data.getString(KEY_CLIENT_ID, UUID.randomUUID().toString());
                    String channelType = data.getString(KEY_CHANNEL_TYPE, CHANNEL_MESSENGER);
                    AbstractChannelService service = serviceRef.get();
                    if (service != null) {
                        ChannelServer channelServer = service.getChannelServer(msgClientId, channelType);
                        if (channelServer != null) {
                            channelServer.handleMessage(msg);
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    @NonNull
    public final IBinder onBind(Intent intent) {
        String clientId = getClientIdFromIntent(intent);
        String channelType = getChannelTypeFromIntent(intent);
        Log.d(TAG, String.format("Bound to client %s channel type: %s", clientId, channelType));
        return createServiceIncomingHandler(clientId, channelType);
    }

    @NonNull
    public IBinder createServiceIncomingHandler(String clientId, String channelType) {
        synchronized (channelServerMap) {
            // Other handlers can be selected here if/when they are implemented
            serviceName = getServiceName();

            ChannelServer channelServer = getChannelServer(clientId, channelType);
            incomingHandler = new IncommingHandler(this);
            Messenger incomingMessenger = getMessenger();
            monitorForDeath(incomingMessenger, channelServer);
            channelServerMap.put(clientId, channelServer);
            return incomingMessenger.getBinder();
        }
    }

    /**
     * Set this flag if you want the service to stop itself once all client channels have unbound
     *
     * @param stopSelfOnEndOfStream If true this service will stop once all client channels have unbound
     */
    public void setStopSelfOnEndOfStream(boolean stopSelfOnEndOfStream) {
        this.stopSelfOnEndOfStream = stopSelfOnEndOfStream;
    }

    private String getServiceName() {
        return new ComponentName(getPackageName(), getClass().getName()).flattenToString();
    }

    @NonNull
    protected Messenger getMessenger() {
        return new Messenger(incomingHandler);
    }

    protected void monitorForDeath(Messenger incomingMessenger, final ChannelServer channelServer) {
        if (incomingMessenger != null) {
            IBinder binder = incomingMessenger.getBinder();
            if (binder != null) {
                try {
                    binder.linkToDeath(channelServer, 0);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to link to binder death. We won't know if this messenger binding disappears", e);
                }
            }
        }
    }

    /**
     * Supply a {@link ChannelServer} based on the channelType sent in the Intent onBind of this service
     * <p>
     * Can be overridden in services if a custom channel server is required
     *
     * @param channelType The channel type to support
     * @return A {@link ChannelServer} implementation
     */
    @NonNull
    protected ChannelServer getChannelServer(String clientId, String channelType) {
        if (channelServerMap.containsKey(clientId)) {
            return channelServerMap.get(clientId);
        } else {
            ChannelServer channelServer = ChannelServerFactory.getChannelServer(getBaseContext(), channelType, serviceName);
            channelServerMap.put(clientId, channelServer);
            onNewClient(channelServer, getCallingPackage());
            return channelServer;
        }
    }

    protected String getCallingPackage() {
        return getBaseContext().getPackageManager().getNameForUid(Binder.getCallingUid());
    }

    /**
     * Should be implemented by services extending this class to handle new client connections
     *
     * @param incomingHandler    The handler assigned to client for sending and receiving messages
     * @param callingPackageName The packageName of the calling client application
     */
    protected abstract void onNewClient(ChannelServer incomingHandler, String callingPackageName);

    @Override
    public boolean onUnbind(Intent intent) {
        synchronized (channelServerMap) {
            String clientId = getClientIdFromIntent(intent);
            Log.d(TAG, String.format("Unbound from client %s", clientId));
            ChannelServer channel = getTargetForClientId(clientId);
            if (channel != null) {
                channel.clientDispose();
            }
            channelServerMap.remove(clientId);
            checkForStop();
        }
        return false;
    }

    private void checkForStop() {
        if (channelServerMap.size() == 0 && stopSelfOnEndOfStream) {
            stopSelf();
        }
    }

    /**
     * Returns the {@link ChannelServer} for a client
     *
     * @param clientMessageId The id of the client to get the {@link ChannelServer} for
     * @return A {@link ChannelServer} if one exists for the client Id given or null if not
     */
    @Nullable
    public ChannelServer getChannelServerForId(String clientMessageId) {
        return channelServerMap.get(clientMessageId);
    }

    @NonNull
    private String getClientIdFromIntent(Intent intent) {
        if (intent.hasExtra(KEY_CLIENT_ID)) {
            return intent.getStringExtra(KEY_CLIENT_ID);
        }
        // fallback to use package name, Uid and Pid from bound Intent if no client id sent
        return getCallingPackage() + ":" + Binder.getCallingUid() + ":" + Binder.getCallingPid();
    }

    @NonNull
    private String getChannelTypeFromIntent(Intent intent) {
        if (intent.hasExtra(KEY_CHANNEL_TYPE)) {
            return intent.getStringExtra(KEY_CHANNEL_TYPE);
        }
        return CHANNEL_MESSENGER;
    }

    private ChannelServer getTargetForClientId(String clientId) {
        return channelServerMap.get(clientId);
    }
}
