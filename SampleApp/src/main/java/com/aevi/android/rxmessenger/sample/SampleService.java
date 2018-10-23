package com.aevi.android.rxmessenger.sample;


import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.aevi.android.rxmessenger.ChannelServer;
import com.aevi.android.rxmessenger.activity.ObservableActivityHelper;
import com.aevi.android.rxmessenger.service.AbstractChannelService;
import com.google.gson.Gson;

import static android.arch.lifecycle.Lifecycle.Event.ON_DESTROY;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class SampleService extends AbstractChannelService implements ChannelServer.ClientListener {

    private static final String TAG = SampleService.class.getSimpleName();
    private final Gson gson;
    private volatile boolean responseSent;

    public SampleService() {
        gson = new Gson();
    }

    @Override
    protected void onNewClient(ChannelServer channelServer, String callingPackageName) {

        Toast.makeText(this, "New client connected", Toast.LENGTH_SHORT).show();

        channelServer.addClientListener(this);
        channelServer.subscribeToMessages().subscribe(message -> {
            Log.d(TAG, String.format("Client sent message: %s ", message));
            SampleMessage sampleMessage = gson.fromJson(message, SampleMessage.class);
            switch (sampleMessage.getMessageType()) {
                case MessageTypes.START_ACTIVITY:
                    startObservableActivity(channelServer);
                    break;
                case MessageTypes.END_STREAM:
                    // This is just for illustrating how to end stream from here - in reality the client would never request this
                    channelServer.send(gson.toJson(new SampleMessage(MessageTypes.RESPONSE,"You asked to end")));
                    channelServer.sendEndStream();
                    channelServer.clientClose();
                    break;
            }
        });
    }

    private void startObservableActivity(final ChannelServer channelServer) {
        responseSent = false;
        Intent intent = new Intent(this, SampleActivity.class);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        ObservableActivityHelper<String> activityHelper = ObservableActivityHelper.createInstance(this, intent);

        // Registering to this allows the service to react to lifecycle events from the activity it has started
        activityHelper.onLifecycleEvent().subscribe(event -> {
            // All we do here is log it - but in a real implementation you may want to react to onStop, onDestroy, etc
            Log.d(TAG, "Received activity lifecycle event: " + event);
            activityHelper.sendEventToActivity("Hello, I received this event: " + event);
            if (event == ON_DESTROY && !responseSent) {
                SampleMessage sampleMessage = new SampleMessage(MessageTypes.RESPONSE, "Activity destroyed without sending response");
                channelServer.send(gson.toJson(sampleMessage));
                channelServer.sendEndStream();
            }
        });

        activityHelper.startObservableActivity().subscribe(responseFromActivity -> {
            Log.d(TAG, "Response from activity: " + responseFromActivity + " sending to client");
            SampleMessage sampleMessage = new SampleMessage(MessageTypes.RESPONSE, responseFromActivity);
            channelServer.send(gson.toJson(sampleMessage));
            responseSent = true;
        });

    }

    @Override
    public void onClientClosed() {
        Toast.makeText(this, "Client messaging ended by service", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClientDispose() {
        Toast.makeText(this, "Client has disconnected", Toast.LENGTH_SHORT).show();
    }
}
