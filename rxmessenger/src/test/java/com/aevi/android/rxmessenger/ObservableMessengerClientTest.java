package com.aevi.android.rxmessenger;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;

import static com.aevi.android.rxmessenger.AbstractMessengerService.KEY_DATA_REQUEST;
import static com.aevi.android.rxmessenger.AbstractMessengerService.KEY_DATA_RESPONSE;
import static com.aevi.android.rxmessenger.AbstractMessengerService.MESSAGE_END_STREAM;
import static com.aevi.android.rxmessenger.AbstractMessengerService.MESSAGE_ERROR;
import static com.aevi.android.rxmessenger.AbstractMessengerService.MESSAGE_REQUEST;
import static com.aevi.android.rxmessenger.AbstractMessengerService.MESSAGE_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

@Config(sdk = Build.VERSION_CODES.LOLLIPOP, manifest = Config.NONE, shadows = {MockShadowMessenger.class})
@RunWith(RobolectricTestRunner.class)
public class ObservableMessengerClientTest {

    private String MOCK_SERVICE_PACKAGE = "com.my.package";
    private String MOCK_SERVICE_CLASS = "com.my.package.MyServiceClass";

    private ObservableMessengerClient<DataObject, DataObject> observableMessengerClient;
    private MockMessageService mockMessageService;

    @Before
    public void setupMessengerClient() {
        ShadowLog.stream = System.out;
        initMocks(this);
        observableMessengerClient = new ObservableMessengerClient<>(RuntimeEnvironment.application, DataObject.class);
        MockShadowMessenger.clearMessages();
    }

    @Test
    public void checkWillHandleNoPaymentControlServiceWithError() throws RemoteException {
        TestObserver<DataObject> obs = createObservableSendDataAndSubscribe(new DataObject());

        obs.assertError(RuntimeException.class);
    }

    @Test
    public void checkWillUnbindService() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe(new DataObject());

        verifyServiceUnbound();
    }

    @Test
    public void checkWillSendMessageToPcs() throws RemoteException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        createObservableSendDataAndSubscribe(msg);

        verifyDataSent(msg);
    }

    @Test
    public void checkWillReceiveMessageFromService() throws RemoteException, InterruptedException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        TestObserver<DataObject> obs = createObservableSendDataAndSubscribe(msg);

        DataObject response = new DataObject();
        sendReply(response);

        obs.awaitDone(2000, TimeUnit.MILLISECONDS).assertNoErrors().assertNotComplete().assertValue(response);
    }

    @Test
    public void checkWillReceiveMultipleMessagesFromService() throws RemoteException, InterruptedException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        TestObserver<DataObject> obs = createObservableSendDataAndSubscribe(msg);

        DataObject response1 = new DataObject();
        DataObject response2 = new DataObject();
        DataObject response3 = new DataObject();
        DataObject response4 = new DataObject();
        sendReply(response1);
        sendReply(response2);
        sendReply(response3);
        sendReply(response4);

        obs.awaitDone(2000, TimeUnit.MILLISECONDS).assertNoErrors().assertNotComplete().assertValues(response1, response2, response3, response4);
    }

    @Test
    public void checkWillEndStreamWhenToldByService() throws RemoteException, InterruptedException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        TestObserver<DataObject> obs = createObservableSendDataAndSubscribe(msg);

        DataObject response = new DataObject();
        sendReply(response);
        sendEndStream();

        obs.awaitDone(2000, TimeUnit.MILLISECONDS).assertNoErrors().assertComplete().assertValue(response);
    }


    @Test
    public void checkWillReceiveErrorMessageFromService() throws RemoteException, InterruptedException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        TestObserver<DataObject> obs = createObservableSendDataAndSubscribe(msg);

        sendErrorReply("code", "description");

        obs.awaitDone(2000, TimeUnit.MILLISECONDS).assertError(new Predicate<Throwable>() {
            @Override
            public boolean test(@NonNull Throwable throwable) throws Exception {
                return new MessageException("code", "description").equals(throwable);
            }
        });
    }

    @Test
    public void checkWillIgnoreNullMessageFromPcs() throws RemoteException, InterruptedException {
        TestObserver<DataObject> actionTestObserver = createObservableSendDataAndSubscribe(null);

        actionTestObserver.awaitDone(2000, TimeUnit.MILLISECONDS).assertNotComplete().assertTimeout();
    }

    @Test
    public void checkCanFunctionOffMainThread() throws InterruptedException {
        DataObject msg = new DataObject();
        NotMainRunnable nmr = new NotMainRunnable(msg);
        new Thread(nmr).start();
        nmr.startSignal.await(5000, TimeUnit.MILLISECONDS);
        nmr.obs.awaitDone(2000, TimeUnit.MILLISECONDS).assertNoErrors().assertNotComplete();
    }

    public class NotMainRunnable implements Runnable {

        CountDownLatch startSignal = new CountDownLatch(1);
        final DataObject msg;
        TestObserver<DataObject> obs;

        NotMainRunnable(DataObject msg) {
            this.msg = msg;
        }

        public void run() {
            setupMockBoundMessengerService();
            obs = createObservableSendDataAndSubscribe(msg);
            startSignal.countDown();
        }
    }

    private void sendEndStream() throws RemoteException {
        Message m = Message.obtain();
        m.what = MESSAGE_END_STREAM;
        Message sent = MockShadowMessenger.getMessages().get(0);
        sent.replyTo.send(m);
    }

    private void sendReply(DataObject response) throws RemoteException {
        Message m = Message.obtain();
        m.what = MESSAGE_RESPONSE;
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, response.toJson());
        m.setData(b);
        Message sent = MockShadowMessenger.getMessages().get(0);
        sent.replyTo.send(m);
    }

    private void sendErrorReply(String code, String desc) throws RemoteException {
        Message m = Message.obtain();
        m.what = MESSAGE_ERROR;
        Bundle b = new Bundle();
        b.putString(KEY_DATA_RESPONSE, new MessageException(code, desc).toJson());
        m.setData(b);
        Message sent = MockShadowMessenger.getMessages().get(0);
        sent.replyTo.send(m);
    }

    private void verifyDataSent(DataObject msg) {
        assertThat(MockShadowMessenger.getMessages()).hasSize(1);
        Message m = MockShadowMessenger.getMessages().get(0);
        Bundle b = m.getData();
        assertThat(b).isNotNull();
        assertThat(m.what).isEqualTo(MESSAGE_REQUEST);
        assertThat(b.getString(KEY_DATA_REQUEST)).isNotNull();
        assertThat(b.getString(KEY_DATA_REQUEST)).isEqualTo(msg.toJson());
    }

    private TestObserver<DataObject> createObservableSendDataAndSubscribe(DataObject dataObject) {
        Intent intent = getMockServiceIntent();
        return observableMessengerClient.createObservableForServiceIntent(intent, dataObject).test();
    }

    private void verifyServiceUnbound() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        assertThat(shadowApplication.getUnboundServiceConnections()).hasSize(1);
    }

    private void setupMockBoundMessengerService() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        mockMessageService = new MockMessageService();

        shadowApplication.setComponentNameAndServiceForBindService(new ComponentName(MOCK_SERVICE_PACKAGE, MOCK_SERVICE_CLASS), mockMessageService.onBind(null));

        Intent intent = getMockServiceIntent();

        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager());
        shadowPackageManager.addResolveInfoForIntent(intent, new ResolveInfo());
    }

    private Intent getMockServiceIntent() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(MOCK_SERVICE_PACKAGE, MOCK_SERVICE_CLASS));
        return intent;
    }

    private class MockMessageService extends Service {

        private final Messenger incomingMessenger = mock(Messenger.class);

        @Override
        public IBinder onBind(Intent intent) {
            return incomingMessenger.getBinder();
        }
    }

    private class DataObject implements Sendable {

        private String id;

        public DataObject() {
            id = UUID.randomUUID().toString();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String toJson() {
            return JsonConverter.serialize(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DataObject that = (DataObject) o;

            return id != null ? id.equals(that.id) : that.id == null;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

}
