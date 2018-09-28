package com.aevi.android.rxmessenger.sample;

import android.content.ComponentName;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.aevi.android.rxmessenger.client.ObservableMessengerClient;
import com.google.gson.Gson;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ClientActivity extends AppCompatActivity {

    private static final ComponentName SERVICE =
            new ComponentName("com.aevi.android.rxmessenger.sample", "com.aevi.android.rxmessenger.sample.SampleService");

    private ObservableMessengerClient messengerClient;
    private Gson gson;

    @BindView(R.id.connection_status)
    TextView status;

    @BindView(R.id.message)
    TextView message;

    public ClientActivity() {
        gson = new Gson();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        messengerClient = new ObservableMessengerClient(this, SERVICE);
    }

    @OnClick(R.id.bind)
    public void onBindToService() {
        messengerClient.connect().subscribe(() -> status.setText(R.string.connected_to_service_with_stream),
                throwable -> status.setText(getString(R.string.failed_to_connect, throwable.getMessage())));
    }

    @OnClick(R.id.send_request)
    public void onStartRemoteActivity() {
        SampleMessage sampleMessage = new SampleMessage(MessageTypes.START_ACTIVITY);
        messengerClient.sendMessage(gson.toJson(sampleMessage))
                .doOnSubscribe(disposable -> status.setText(R.string.connected_to_service_with_stream))
                .doOnComplete(() -> status.setText(R.string.connected_to_service_no_stream))
                .subscribe(response -> message.setText(getString(R.string.received_response, gson.fromJson(response, SampleMessage.class).getMessageData())));
    }

    @OnClick(R.id.disconnect)
    public void onDisconnect() {
        messengerClient.closeConnection();
        status.setText(R.string.not_connected);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        messengerClient.closeConnection();
    }
}
