package com.aevi.android.rxmessenger.client;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.aevi.android.rxmessenger.MockShadowMessenger;
import com.aevi.android.rxmessenger.service.pipe.Pipe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.IOException;
import java.util.List;

import io.reactivex.observers.TestObserver;

import static com.aevi.android.rxmessenger.MessageConstants.CHANNEL_PIPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CHANNEL_TYPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CLIENT_ID;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_PIPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_RESPONSE;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_RESPONSE;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@Config(manifest = Config.NONE, shadows = {MockShadowMessenger.class})
@RunWith(RobolectricTestRunner.class)
public class ObservablePipeClientTest {

    private static final String MOCK_SERVICE_PACKAGE = "com.my.package";
    private static final String MOCK_SERVICE_CLASS = "com.my.package.MyServiceClass";
    private static final ComponentName SERVICE_COMPONENT_NAME = new ComponentName(MOCK_SERVICE_PACKAGE, MOCK_SERVICE_CLASS);

    private TestObservablePipeClient observablePipeClient;

    private MockMessageService mockMessageService;

    @Before
    public void setupMessengerClient() {
        ShadowLog.stream = System.out;
        initMocks(this);
        observablePipeClient = new TestObservablePipeClient(RuntimeEnvironment.application, SERVICE_COMPONENT_NAME);
        MockShadowMessenger.clearMessages();
    }

    @Test
    public void checkWillHandleNoServiceWithError() throws RemoteException {
        setupServiceUnbindable();

        TestObserver<String> obs = createObservableSendDataAndSubscribe("");

        obs.assertError(NoSuchServiceException.class);
    }

    @Test
    public void checkServiceDoesNotUnbindImmediately() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("");

        verifyServiceIsBound();
    }

    @Test
    public void checkBindIntentWillContainCorrectChannel() {

        Intent bindIntent = observablePipeClient.getServiceIntent("7878");

        assertThat(bindIntent).isNotNull();
        assertThat(bindIntent.hasExtra(KEY_CHANNEL_TYPE)).isTrue();
        assertThat(bindIntent.getStringExtra(KEY_CHANNEL_TYPE)).isEqualTo(CHANNEL_PIPE);
        assertThat(bindIntent.hasExtra(KEY_CLIENT_ID)).isTrue();
        assertThat(bindIntent.getStringExtra(KEY_CLIENT_ID)).isEqualTo("7878");
    }

    @Test
    public void willSendMessageViaSocket() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("hellooooo");
        sendPipeFromServer();
        setSocketConnectedState(true);

        observablePipeClient.sendMessage("This is ground control to Major Tom").test();

        verify(observablePipeClient.pipe).write("This is ground control to Major Tom");
    }

    @Test
    public void willFallbackToMessengerIfNotConnected() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("connect with me");

        observablePipeClient.sendMessage("Stop messaging me").test();

        verify(observablePipeClient.pipe, times(0)).write(anyString());
        verifyMessagesSentToServerViaMessenger(2);
    }

    @Test
    public void willCloseSocket() throws RemoteException, IOException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("lets connect");
        sendPipeFromServer();
        setSocketConnectedState(true);

        observablePipeClient.closeConnection();

        verify(observablePipeClient.pipe).close();
    }

    private void setSocketConnectedState(boolean connectedState) {
        observablePipeClient.setConnectionStatus(connectedState);
    }

    private TestObserver<String> createObservableSendDataAndSubscribe(String message) {
        return observablePipeClient.sendMessage(message).test();
    }

    private void sendPipeFromServer() throws RemoteException {
        ParcelFileDescriptor descriptor = mock(ParcelFileDescriptor.class);
        Bundle b = new Bundle();
        b.putParcelable(KEY_DATA_PIPE, descriptor);
        sendReply(b);
    }

    private void sendReply(Bundle b) throws RemoteException {
        Message m = Message.obtain();
        m.what = MESSAGE_RESPONSE;
        m.setData(b);
        Message sent = MockShadowMessenger.getMessages().get(0);
        sent.replyTo.send(m);
    }

    private void sendReply(String response) throws RemoteException {
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, response);
        sendReply(b);
    }

    private void verifyMessagesSentToServerViaMessenger(int expectedSize) {
        List<Message> msgs = MockShadowMessenger.getMessages();
        assertThat(msgs).hasSize(expectedSize);
    }

    private void verifyServiceIsBound() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        assertThat(shadowApplication.getBoundServiceConnections()).hasSize(1);
    }

    private void setupServiceUnbindable() {
        Intent intent = new Intent();
        intent.setComponent(SERVICE_COMPONENT_NAME);
        ShadowApplication.getInstance().declareActionUnbindable(intent.getAction());
    }

    private void setupMockBoundMessengerService() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        mockMessageService = new ObservablePipeClientTest.MockMessageService();

        shadowApplication.setComponentNameAndServiceForBindService(new ComponentName(MOCK_SERVICE_PACKAGE, MOCK_SERVICE_CLASS), mockMessageService.onBind(null));

        Intent intent = new Intent();
        intent.setComponent(SERVICE_COMPONENT_NAME);

        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager());
        shadowPackageManager.addResolveInfoForIntent(intent, new ResolveInfo());
    }

    private class MockMessageService extends Service {

        private final Messenger incomingMessenger = mock(Messenger.class);

        Intent bindIntent;

        @Override
        public IBinder onBind(Intent intent) {
            this.bindIntent = intent;
            return incomingMessenger.getBinder();
        }
    }

    private class TestObservablePipeClient extends ObservablePipeClient {

        Pipe pipe;

        public TestObservablePipeClient(Context context, ComponentName serviceComponentName) {
            super(context, serviceComponentName);
            pipe = mock(Pipe.class);
        }

        @Override
        protected Pipe getPipe(ParcelFileDescriptor descriptor) {
            return pipe;
        }

        void setConnectionStatus(boolean connected) {
            when(pipe.isConnected()).thenReturn(connected);
        }
    }
}
