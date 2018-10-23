package com.aevi.android.rxmessenger.client;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.aevi.android.rxmessenger.MockShadowMessenger;
import com.aevi.android.rxmessenger.client.websocket.OkWebSocketClient;
import com.aevi.android.rxmessenger.model.ConnectionParams;
import com.google.gson.GsonBuilder;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.List;

import io.reactivex.observers.TestObserver;

import static com.aevi.android.rxmessenger.MessageConstants.CHANNEL_WEBSOCKET;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CHANNEL_TYPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CLIENT_ID;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_RESPONSE;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_RESPONSE;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@Config(sdk = Build.VERSION_CODES.LOLLIPOP, manifest = Config.NONE, shadows = {MockShadowMessenger.class})
@RunWith(RobolectricTestRunner.class)
public class ObservableWebSocketClientTest {

    private static final String MOCK_SERVICE_PACKAGE = "com.my.package";
    private static final String MOCK_SERVICE_CLASS = "com.my.package.MyServiceClass";
    private static final ComponentName SERVICE_COMPONENT_NAME = new ComponentName(MOCK_SERVICE_PACKAGE, MOCK_SERVICE_CLASS);

    private TestObservableWebSocketClient observableWebSocketClient;

    private MockMessageService mockMessageService;

    @Before
    public void setupMessengerClient() {
        ShadowLog.stream = System.out;
        initMocks(this);
        observableWebSocketClient = new TestObservableWebSocketClient(RuntimeEnvironment.application, SERVICE_COMPONENT_NAME);
        MockShadowMessenger.clearMessages();
    }

    @Test
    public void checkWillHandleNoServiceWithError() throws RemoteException {
        TestObserver<String> obs = createObservableSendDataAndSubscribe("");

        obs.assertError(RuntimeException.class);
    }

    @Test
    public void checkServiceDoesNotUnbindImmediately() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("");

        verifyServiceIsBound();
    }

    @Test
    public void checkBindIntentWillContainCorrectChannel() {

        Intent bindIntent = observableWebSocketClient.getServiceIntent("7878");

        assertThat(bindIntent).isNotNull();
        assertThat(bindIntent.hasExtra(KEY_CHANNEL_TYPE)).isTrue();
        assertThat(bindIntent.getStringExtra(KEY_CHANNEL_TYPE)).isEqualTo(CHANNEL_WEBSOCKET);
        assertThat(bindIntent.hasExtra(KEY_CLIENT_ID)).isTrue();
        assertThat(bindIntent.getStringExtra(KEY_CLIENT_ID)).isEqualTo("7878");
    }

    @Test
    public void willSetupFromConnectionParams() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("I think we've made a connection");

        sendConnectionParamsFromServer();

        assertThat(observableWebSocketClient.params.getHostAddress()).isEqualTo("0.1.2.3");
        assertThat(observableWebSocketClient.params.getPort()).isEqualTo(3636);
    }

    @Test
    public void willSendMessageViaWebsocket() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("hellooooo");
        sendConnectionParamsFromServer();
        setWebSocketConnectedState(true);

        observableWebSocketClient.sendMessage("This is ground control to Major Tom").test();

        verify(observableWebSocketClient.okWebSocketClient).sendMessage("This is ground control to Major Tom");
    }

    @Test
    public void willFallbackToMessengerIfNotConnected() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("connect with me");
        sendConnectionParamsFromServer();
        setWebSocketConnectedState(false);

        observableWebSocketClient.sendMessage("Stop messaging me").test();

        verify(observableWebSocketClient.okWebSocketClient, times(0)).sendMessage(anyString());
        verifyMessagesSentToServerViaMessenger(2);
    }

    @Test
    public void willCloseWebSocket() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe("lets connect");
        sendConnectionParamsFromServer();
        setWebSocketConnectedState(true);

        observableWebSocketClient.closeConnection();

        verify(observableWebSocketClient.okWebSocketClient).close();
    }

    @Test
    @Ignore
    public void willCompleteOnWebSocketClosedByServer() {
        // TODO
    }

    private void setWebSocketConnectedState(boolean connectedState) {
        observableWebSocketClient.setConnectionStatus(connectedState);
    }

    private TestObserver<String> createObservableSendDataAndSubscribe(String message) {
        return observableWebSocketClient.sendMessage(message).test();
    }

    private void sendConnectionParamsFromServer() throws RemoteException {
        ConnectionParams connectionParams = new ConnectionParams("0.1.2.3", 3636);
        sendReply(new GsonBuilder().create().toJson(connectionParams));
    }

    private void sendReply(String response) throws RemoteException {
        Message m = Message.obtain();
        m.what = MESSAGE_RESPONSE;
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, response);
        m.setData(b);
        Message sent = MockShadowMessenger.getMessages().get(0);
        sent.replyTo.send(m);
    }

    private void verifyMessagesSentToServerViaMessenger(int expectedSize) {
        List<Message> msgs = MockShadowMessenger.getMessages();
        assertThat(msgs).hasSize(expectedSize);
    }

    private void verifyServiceIsBound() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        assertThat(shadowApplication.getBoundServiceConnections()).hasSize(1);
    }

    private void setupMockBoundMessengerService() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        mockMessageService = new ObservableWebSocketClientTest.MockMessageService();

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

    private class TestObservableWebSocketClient extends ObservableWebSocketClient {

        OkWebSocketClient okWebSocketClient;
        ConnectionParams params;

        public TestObservableWebSocketClient(Context context, ComponentName serviceComponentName) {
            super(context, serviceComponentName);
            okWebSocketClient = mock(OkWebSocketClient.class);
        }

        @Override
        protected OkWebSocketClient getWebSocketClient(ConnectionParams params) {
            this.params = params;
            return okWebSocketClient;
        }

        void setConnectionStatus(boolean connected) {
            when(okWebSocketClient.isConnected()).thenReturn(connected);
        }
    }
}
