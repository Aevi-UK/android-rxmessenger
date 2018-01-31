package com.aevi.android.rxmessenger.sample;


import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.aevi.android.rxmessenger.activity.ObservableActivityHelper;
import com.aevi.android.rxmessenger.service.AbstractMessengerService;
import com.google.gson.Gson;

import static android.arch.lifecycle.Lifecycle.Event.ON_DESTROY;

public class SampleService extends AbstractMessengerService {

    private static final String TAG = SampleService.class.getSimpleName();
    private final Gson gson;
    private volatile boolean responseSent;

    public SampleService() {
        gson = new Gson();
    }

    @Override
    protected void handleRequest(String clientId, String requestData, String packageName) {
        Log.d(TAG, String.format("Client (%s) sent message: %s ", clientId, requestData));
        SampleMessage sampleMessage = gson.fromJson(requestData, SampleMessage.class);
        switch (sampleMessage.getMessageType()) {
            case MessageTypes.START_ACTIVITY:
                startObservableActivity(clientId);
                break;
            case MessageTypes.END_STREAM:
                // This is just for illustrating how to end stream from here - in reality the client would never request this
                sendEndStreamMessageToClient(clientId);
                break;
        }
    }

    private void startObservableActivity(final String clientId) {
        responseSent = false;
        Intent intent = new Intent(this, SampleActivity.class);
        ObservableActivityHelper<String> activityHelper = ObservableActivityHelper.createInstance(this, intent);

        // Registering to this allows the service to react to lifecycle events from the activity it has started
        activityHelper.onLifecycleEvent().subscribe(event -> {
            // All we do here is log it - but in a real implementation you may want to react to onStop, onDestroy, etc
            Log.d(TAG, "Received activity lifecycle event: " + event);
            activityHelper.sendEventToActivity("Hello, I received this event: " + event);
            if (event == ON_DESTROY && !responseSent) {
                SampleMessage sampleMessage = new SampleMessage(MessageTypes.RESPONSE, "Activity destroyed without sending response");
                sendMessageToClient(clientId, gson.toJson(sampleMessage));
                sendEndStreamMessageToClient(clientId);
            }
        });

        activityHelper.startObservableActivity().subscribe(responseFromActivity -> {
            Log.d(TAG, "Response from activity: " + responseFromActivity);
            SampleMessage sampleMessage = new SampleMessage(MessageTypes.RESPONSE, responseFromActivity);
            sendMessageToClient(clientId, gson.toJson(sampleMessage));
            responseSent = true;
        });

    }

    @Override
    protected void onNewClient(Intent intent) {
        Toast.makeText(this, "New client connected", Toast.LENGTH_SHORT).show();
    }
}
