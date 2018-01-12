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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

import static com.aevi.android.rxmessenger.AbstractMessengerService.*;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

@Config(sdk = Build.VERSION_CODES.LOLLIPOP, manifest = Config.NONE, shadows = {MockShadowMessenger.class})
@RunWith(RobolectricTestRunner.class)
public class ObservableMessengerClientTest {

    private String MOCK_SERVICE_PACKAGE = "com.my.package";
    private String MOCK_SERVICE_CLASS = "com.my.package.MyServiceClass";

    private ObservableMessengerClient observableMessengerClient;
    private MockMessageService mockMessageService;

    @Before
    public void setupMessengerClient() {
        ShadowLog.stream = System.out;
        initMocks(this);
        observableMessengerClient = new ObservableMessengerClient(RuntimeEnvironment.application);
        MockShadowMessenger.clearMessages();
    }

    @Test
    public void checkWillHandleNoPaymentControlServiceWithError() throws RemoteException {
        TestObserver<String> obs = createObservableSendDataAndSubscribe(new DataObject());

        obs.assertError(RuntimeException.class);
    }

    @Test
    public void checkServiceDoesNotUnbindImmediately() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe(new DataObject());

        verifyServiceIsBound();
    }

    @Test
    public void checkDisposeWillUnbindService() throws RemoteException {
        setupMockBoundMessengerService();
        createObservableSendDataAndSubscribe(new DataObject()).dispose();

        verifyServiceIsUnbound();
    }

    @Test
    public void checkCompleteWillUnbindService() throws RemoteException {
        setupMockBoundMessengerService();
        TestObserver<String> obs = createObservableSendDataAndSubscribe(new DataObject());

        DataObject response = new DataObject();
        sendReply(response);
        sendEndStream();

        obs.awaitDone(2000, TimeUnit.MILLISECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValue(response.toJson());

        verifyServiceIsUnbound();
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
        TestObserver<String> obs = createObservableSendDataAndSubscribe(msg);

        DataObject response = new DataObject();
        sendReply(response);

        obs.awaitDone(2000, TimeUnit.MILLISECONDS)
                .assertNoErrors()
                .assertNotComplete()
                .assertValue(response.toJson());
    }

    @Test
    public void checkWillReceiveMultipleMessagesFromService() throws RemoteException, InterruptedException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        TestObserver<String> obs = createObservableSendDataAndSubscribe(msg);

        DataObject response1 = new DataObject();
        DataObject response2 = new DataObject();
        DataObject response3 = new DataObject();
        DataObject response4 = new DataObject();
        sendReply(response1);
        sendReply(response2);
        sendReply(response3);
        sendReply(response4);

        obs.awaitDone(2000, TimeUnit.MILLISECONDS)
                .assertNoErrors()
                .assertNotComplete()
                .assertValues(response1.toJson(), response2.toJson(), response3.toJson(), response4.toJson());
    }

    @Test
    public void checkWillEndStreamWhenToldByService() throws RemoteException, InterruptedException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        TestObserver<String> obs = createObservableSendDataAndSubscribe(msg);

        DataObject response = new DataObject();
        sendReply(response);
        sendEndStream();

        obs.awaitDone(2000, TimeUnit.MILLISECONDS)
                .assertNoErrors()
                .assertComplete()
                .assertValue(response.toJson());
    }

    @Test
    public void checkWillReceiveErrorMessageFromService() throws RemoteException, InterruptedException {
        setupMockBoundMessengerService();
        DataObject msg = new DataObject();
        TestObserver<String> obs = createObservableSendDataAndSubscribe(msg);

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
        TestObserver<String> actionTestObserver = createObservableSendDataAndSubscribe(null);

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
        TestObserver<String> obs;

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

    private TestObserver<String> createObservableSendDataAndSubscribe(DataObject dataObject) {
        Intent intent = getMockServiceIntent();
        return observableMessengerClient.createObservableForServiceIntent(intent, dataObject == null ? null : dataObject.toJson()).test();
    }

    private void verifyServiceIsUnbound() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        assertThat(shadowApplication.getBoundServiceConnections()).isEmpty();
        assertThat(shadowApplication.getUnboundServiceConnections()).hasSize(1);
    }
    private void verifyServiceIsBound() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        assertThat(shadowApplication.getBoundServiceConnections()).hasSize(1);
    }

    private void setupMockBoundMessengerService() {
        ShadowApplication shadowApplication = ShadowApplication.getInstance();
        mockMessageService = new MockMessageService();

        shadowApplication.setComponentNameAndServiceForBindService(new ComponentName(MOCK_SERVICE_PACKAGE, MOCK_SERVICE_CLASS),
                mockMessageService.onBind(null));

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

    private class DataObject {

        transient final Gson gson = new GsonBuilder().create();

        private String id;

        public DataObject() {
            id = UUID.randomUUID().toString();
        }

        public String getId() {
            return id;
        }

        public String toJson() {
            return gson.toJson(this);
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
