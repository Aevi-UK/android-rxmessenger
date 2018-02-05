package com.aevi.android.rxmessenger.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.aevi.android.rxmessenger.activity.NoSuchInstanceException;
import com.aevi.android.rxmessenger.activity.ObservableActivityHelper;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class SampleActivity extends AppCompatActivity {

    private static final String TAG = SampleActivity.class.getSimpleName();

    @BindView(R.id.response_text)
    EditText responseText;

    @BindView(R.id.message)
    TextView messageText;

    private ObservableActivityHelper<String> activityHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);
        ButterKnife.bind(this);
        registerWithHelper();
    }

    private void registerWithHelper() {
        try {
            activityHelper = ObservableActivityHelper.getInstance(getIntent());
            activityHelper.registerForEvents(getLifecycle()).subscribe(eventFromService -> {
                Log.d(TAG, "Received event from service: " + eventFromService);
                messageText.setText(getString(R.string.received_message, eventFromService));
            });
        } catch (NoSuchInstanceException e) {
            // This can happen if the activity wasn't started via the ObservableActivityHelper, or the service has shut down / crashed
            Log.e(TAG, "No activity helper available - finishing");
            finish();
        }
    }

    @OnClick(R.id.send_response)
    public void onSendResponse() {
        String response = responseText.getText().toString();
        activityHelper.publishResponse(response);
    }

    @OnClick(R.id.finish)
    public void onFinish() {
        finish();
    }
}
