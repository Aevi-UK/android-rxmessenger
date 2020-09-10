package com.aevi.android.rxmessenger.sample.client;

import android.content.ComponentName;
import android.os.Bundle;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.aevi.android.rxmessenger.ChannelClient;
import com.aevi.android.rxmessenger.Channels;
import com.aevi.android.rxmessenger.sample.common.MessageTypes;
import com.aevi.android.rxmessenger.sample.common.SampleMessage;
import com.google.gson.Gson;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class ClientActivity extends AppCompatActivity {

    private static final ComponentName SERVICE =
            new ComponentName("com.aevi.android.rxmessenger.sample.server", "com.aevi.android.rxmessenger.sample.server.SampleService");

    private ChannelClient messengerClient;
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
        messengerClient = Channels.pipe(this, SERVICE);
    }

    @OnClick(R.id.bind)
    public void onBindToService() {
        messengerClient.connect().subscribe(() -> status.setText(R.string.connected_to_service_with_stream), throwable -> status.setText(getString(


                R.string.failed_to_connect, throwable.getMessage())));
    }

    @OnClick(R.id.send_request)
    public void onStartRemoteActivity() {
        SampleMessage sampleMessage = new SampleMessage(MessageTypes.START_ACTIVITY);
        messengerClient
                .sendMessage(gson.toJson(sampleMessage))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> status.setText(R.string.connected_to_service_with_stream))
                .doOnComplete(() -> status.setText(R.string.connected_to_service_no_stream))
                .subscribe(response ->
                            message
                                    .setText(getString(R.string.received_response, gson.fromJson(response, SampleMessage.class).getMessageData())));
    }


    @OnClick(R.id.send_end)
    public void onSendEnd() {
        SampleMessage sampleMessage = new SampleMessage(MessageTypes.END_STREAM);
        messengerClient.sendMessage(gson.toJson(sampleMessage))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(() -> messengerClient.closeConnection())
                .subscribe(response ->
                        message
                        .setText(getString(R.string.received_response, gson.fromJson(response, SampleMessage.class).getMessageData())));
    }

    @OnClick(R.id.disconnect)
    public void onDisconnect() {
        messengerClient.closeConnection();
        status.setText(R.string.not_connected);
    }

    @Override
    public void finish() {
        messengerClient.closeConnection();
        messengerClient = null;
        super.finish();
    }
}
