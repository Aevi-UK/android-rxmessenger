package com.aevi.android.rxmessenger.service;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;


import androidx.annotation.NonNull;

import com.aevi.android.rxmessenger.FakeBinder;
import com.aevi.android.rxmessenger.MessageException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import io.reactivex.observers.TestObserver;

import static com.aevi.android.rxmessenger.MessageConstants.KEY_CLIENT_ID;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_REQUEST;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_RESPONSE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_SENDER;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_END_STREAM;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_ERROR;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_REQUEST;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_RESPONSE;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class MessengerChannelServerTest {

    MessengerChannelServer messengerChannelServer;

    @Mock
    Messenger messenger;

    @Mock
    Messenger replyToMessenger;

    @Mock
    IBinder binder = new FakeBinder();

    private String CLIENT_ID = "67367";
    private String COMPONENT_NAME = "com.rxmessenger/.IsKing";
    private String CLIENT_PACKAGE_NAME = "com.rxmessenger.clients.rock";

    @Before
    public void setup() {
        initMocks(this);
        when(messenger.getBinder()).thenReturn(binder);

        messengerChannelServer = new MessengerChannelServer(COMPONENT_NAME, CLIENT_PACKAGE_NAME);
    }

    @Test
    public void checkWillIgnoreEmptyMessage() {
        Message m = setupEmptyMessage();

        TestObserver<String> testObserver = sendMessageAndObserve(m);

        verifyNoMessages(testObserver);
    }

    @Test
    public void checkWillStillSendInvalidJsonMessage() {
        Message m = setupJsonMessage("{ status: INIT; }", CLIENT_ID);

        TestObserver<String> testObserver = sendMessageAndObserve(m);

        assertThat(testObserver.valueCount()).isGreaterThan(0);
        assertThat(testObserver.values().get(0)).isEqualTo("{ status: INIT; }");
    }

    @Test
    public void checkWillIgnoreActionNullMessage() {
        Message m = setupJsonMessage(null, CLIENT_ID);

        TestObserver<String> testObserver = sendMessageAndObserve(m);

        verifyNoMessages(testObserver);
    }

    @Test
    public void checkWillHandleValidMessage() {
        Message m = setupJsonMessage("{ id: 567 }", CLIENT_ID);

        TestObserver<String> testObserver = sendMessageAndObserve(m);

        assertThat(testObserver.valueCount()).isGreaterThan(0);
        assertThat(testObserver.values().get(0)).isEqualTo("{ id: 567 }");
    }

    @Test
    public void willSendMessage() throws RemoteException {
        setupReplyTo();

        String message = "hellooooooo";
        boolean sent = messengerChannelServer.send(message);

        assertThat(sent).isTrue();
        verifySentMessage(MESSAGE_RESPONSE, message);
    }

    @Test
    public void willSendExceptionMessage() throws RemoteException {
        setupReplyTo();

        MessageException message = new MessageException("bleep", "bloop");
        boolean sent = messengerChannelServer.send(message);

        assertThat(sent).isTrue();
        verifySentMessage(MESSAGE_ERROR, message.toJson());
    }

    @Test
    public void willSendEndMessage() throws RemoteException {
        setupReplyTo();

        boolean sent = messengerChannelServer.sendEndStream();

        assertThat(sent).isTrue();
        verifySentMessage(MESSAGE_END_STREAM, null);
    }

    private void verifySentMessage(int type, String message) throws RemoteException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(replyToMessenger).send(captor.capture());
        assertThat(captor.getValue().what).isEqualTo(type);

        Bundle b = captor.getValue().getData();
        assertThat(b).isNotNull();
        if (message != null) {
            assertThat(b.containsKey(KEY_DATA_RESPONSE)).isTrue();
            assertThat(b.getString(KEY_DATA_RESPONSE)).isEqualTo(message);
        } else {
            assertThat(b.containsKey(KEY_DATA_RESPONSE)).isFalse();
        }
        assertThat(b.containsKey(KEY_DATA_SENDER)).isTrue();
        assertThat(b.getString(KEY_DATA_SENDER)).isEqualTo(COMPONENT_NAME);
    }

    private void setupReplyTo() {
        messengerChannelServer.replyTo = replyToMessenger;
    }

    private void verifyNoMessages(TestObserver<String> testObserver) {
        assertThat(testObserver.valueCount()).isEqualTo(0);
    }

    @NonNull
    private TestObserver<String> sendMessageAndObserve(Message m) {
        TestObserver<String> testObserver = messengerChannelServer.subscribeToMessages().test();
        messengerChannelServer.handleMessage(m);
        testObserver.assertNotComplete();
        testObserver.assertNoErrors();
        return testObserver;
    }

    @NonNull
    private Message setupEmptyMessage() {
        return mock(Message.class);
    }

    @NonNull
    private Message setupJsonMessage(String json, String clientId) {
        Message m = new Message();
        m.what = MESSAGE_REQUEST;
        m.replyTo = messenger;
        Bundle b = new Bundle();
        b.putString(KEY_DATA_REQUEST, json);
        b.putString(KEY_CLIENT_ID, clientId);
        m.setData(b);
        return m;
    }
}
