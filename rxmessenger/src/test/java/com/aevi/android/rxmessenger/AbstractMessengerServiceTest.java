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

import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.UUID;

import io.reactivex.annotations.NonNull;

import static com.aevi.android.rxmessenger.AbstractMessengerService.*;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

@Config(sdk = Build.VERSION_CODES.LOLLIPOP, manifest = Config.NONE, shadows = {MockShadowMessenger.class})
@RunWith(RobolectricTestRunner.class)
public class AbstractMessengerServiceTest {

    TestAbstractMessengerService testAbstractMessengerService;

    @Mock
    Messenger clientMessenger;

    @Before
    public void setup() {
        ShadowLog.stream = System.out;
        initMocks(this);
        MockShadowMessenger.clearMessages();
        testAbstractMessengerService = new TestAbstractMessengerService();
    }

    @Test
    public void checkWillIgnoreEmptyMessage() throws RemoteException {
        Message m = setupEmptyMessage();

        testAbstractMessengerService.incomingMessenger.send(m);

        assertThat(testAbstractMessengerService.lastRequestData).isNull();
    }

    @Test
    public void checkWillStillSendInvalidJsonMessage() throws RemoteException {
        Message m = setupJsonMessage("{ status: INIT; }");

        testAbstractMessengerService.incomingMessenger.send(m);

        assertThat(testAbstractMessengerService.lastRequestData).isEqualTo("{ status: INIT; }");
    }

    @Test
    public void checkWillIgnoreActionNullMessage() throws RemoteException {
        Message m = setupJsonMessage(null);

        testAbstractMessengerService.incomingMessenger.send(m);

        assertThat(testAbstractMessengerService.lastRequestData).isNull();
        assertThat(testAbstractMessengerService.lastRequestId).isNull();
    }

    @Test
    public void checkWillHandleValidMessage() throws RemoteException {
        Message m = setupJsonMessage("{ id: 567 }");

        testAbstractMessengerService.incomingMessenger.send(m);

        assertThat(testAbstractMessengerService.lastRequestData).isEqualTo("{ id: 567 }");
    }

    @Test
    public void willHandleValidMessage() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);

        assertThat(testAbstractMessengerService.lastRequestData).isEqualTo(dataObject.toJson());
    }

    @Test
    public void canSendMessageToClient() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);

        DataObject response = new DataObject();
        boolean sent = testAbstractMessengerService.sendMessageToClient(testAbstractMessengerService.lastRequestId, response.toJson());

        verifyDataSentToClient(response);
        assertThat(sent).isTrue();
    }

    @Test
    public void wontSendNullMessageToClient() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);

        boolean sent = testAbstractMessengerService.sendMessageToClient(dataObject.getId(), null);

        verify(clientMessenger, times(0)).send(any(Message.class));
        assertThat(sent).isFalse();
    }

    @Test
    public void wontSendNullIdMessageToClient() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);

        boolean sent = testAbstractMessengerService.sendMessageToClient(null, new DataObject().toJson());

        verify(clientMessenger, times(0)).send(any(Message.class));
        assertThat(sent).isFalse();
    }

    @Test
    public void wontSendUnknownIdMessageToClient() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);

        boolean sent = testAbstractMessengerService.sendMessageToClient("6767", new DataObject().toJson());

        verify(clientMessenger, times(0)).send(any(Message.class));
        assertThat(testAbstractMessengerService.clientMap).hasSize(1);
        assertThat(sent).isFalse();
    }

    @Test
    public void checkClientMessengerThrowIsHandledSafely() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);

        doThrow(new RemoteException("Argh aliens!!")).when(clientMessenger).send(any(Message.class));

        boolean sent = testAbstractMessengerService.sendMessageToClient(dataObject.getId(), new DataObject().toJson());
        assertThat(sent).isFalse();
    }

    @Test
    public void checkCanSendErrorMessengeToClient() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);

        boolean sent = testAbstractMessengerService.sendErrorMessageToClient(testAbstractMessengerService.lastRequestId, "ErrorCode", "Description");

        verifyErrorSentToClient("ErrorCode", "Description");
        assertThat(testAbstractMessengerService.clientMap).hasSize(0);
        assertThat(sent).isTrue();
    }

    @Test
    public void checkCanSendEndMessengeToClient() throws RemoteException {
        DataObject dataObject = new DataObject();
        receiveServiceMessage(dataObject);
        assertThat(testAbstractMessengerService.clientMap).hasSize(1);

        boolean sent = testAbstractMessengerService.sendEndStreamMessageToClient(testAbstractMessengerService.lastRequestId);

        verifyEndSentToClient();
        assertThat(testAbstractMessengerService.clientMap).hasSize(0);
        assertThat(sent).isTrue();
    }

    private void verifyEndSentToClient() throws RemoteException {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(clientMessenger).send(messageCaptor.capture());

        Message m = messageCaptor.getValue();
        Bundle b = m.getData();
        assertThat(b).isNotNull();
        assertThat(m.what).isEqualTo(MESSAGE_END_STREAM);
    }

    private void verifyErrorSentToClient(String code, String msg) throws RemoteException {
        MessageException me = new MessageException(code, msg);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(clientMessenger).send(messageCaptor.capture());

        Message m = messageCaptor.getValue();
        Bundle b = m.getData();
        assertThat(b).isNotNull();
        assertThat(m.what).isEqualTo(MESSAGE_ERROR);
        assertThat(b.getString(KEY_DATA_RESPONSE)).isNotNull();
        assertThat(b.getString(KEY_DATA_RESPONSE)).isEqualTo(me.toJson());
    }

    private void verifyDataSentToClient(DataObject msg) throws RemoteException {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(clientMessenger).send(messageCaptor.capture());

        Message m = messageCaptor.getValue();
        Bundle b = m.getData();
        assertThat(b).isNotNull();
        assertThat(m.what).isEqualTo(MESSAGE_RESPONSE);
        assertThat(b.getString(KEY_DATA_RESPONSE)).isNotNull();
        assertThat(b.getString(KEY_DATA_RESPONSE)).isEqualTo(msg.toJson());
    }

    @NonNull
    private Message setupEmptyMessage() {
        return Message.obtain();
    }

    @NonNull
    private Message setupJsonMessage(String json) {
        Message m = Message.obtain();
        m.what = MESSAGE_REQUEST;
        m.replyTo = clientMessenger;
        Bundle b = new Bundle();
        b.putString(KEY_DATA_REQUEST, json);
        m.setData(b);
        return m;
    }

    @NonNull
    private void receiveServiceMessage(DataObject data) throws RemoteException {
        String json = data.toJson();
        Message m = setupJsonMessage(json);
        testAbstractMessengerService.incomingMessenger.send(m);
    }

    class DataObject {

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

    class TestAbstractMessengerService extends AbstractMessengerService {

        String lastRequestId;
        String lastRequestData;

        protected TestAbstractMessengerService() {
            attachBaseContext(RuntimeEnvironment.application);
        }

        @Override
        protected void handleRequest(String requestId, String request, String packageName) {
            lastRequestData = request;
            lastRequestId = requestId;
        }
    }
}
