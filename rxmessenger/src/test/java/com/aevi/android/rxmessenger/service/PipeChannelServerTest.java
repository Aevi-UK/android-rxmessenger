package com.aevi.android.rxmessenger.service;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.aevi.android.rxmessenger.MessageException;
import com.aevi.android.rxmessenger.service.pipe.Pipe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.CompletableSubject;
import io.reactivex.subjects.PublishSubject;

import static android.content.Context.WIFI_SERVICE;
import static com.aevi.android.rxmessenger.MessageConstants.CHANNEL_WEBSOCKET;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CHANNEL_TYPE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_CLIENT_ID;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_REQUEST;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_RESPONSE;
import static com.aevi.android.rxmessenger.MessageConstants.KEY_DATA_SENDER;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_ERROR;
import static com.aevi.android.rxmessenger.MessageConstants.MESSAGE_REQUEST;
import static com.aevi.android.rxmessenger.service.WebSocketChannelServer.CONNECT_PLEASE;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class PipeChannelServerTest {

    private TestPipeChannelServer pipeChannelServer;

    private String COMPONENT_NAME = "com.rxmessenger/.IsKingAndQueen";
    private String CLIENT_PACKAGE_NAME = "com.rxmessenger.clients.rock";

    @Mock
    Context context;

    @Mock
    WifiManager wifiManager;

    @Mock
    WifiInfo wifiInfo;

    @Mock
    Pipe pipe;

    @Mock
    Messenger replyToMessenger;

    private Scheduler testScheduler = Schedulers.trampoline();

    private PublishSubject<String> messageStream = PublishSubject.create();
    private CompletableSubject disconnectCompletable = CompletableSubject.create();

    @Before
    public void setup() {
        initMocks(this);
        pipeChannelServer = new TestPipeChannelServer(COMPONENT_NAME, CLIENT_PACKAGE_NAME);

        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSystemService(WIFI_SERVICE)).thenReturn(wifiManager);
        when(wifiManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(wifiInfo.getIpAddress()).thenReturn(111222333);
    }

    @Test
    public void willStartServerOnFirstMessage() {
        setupWebserverConnectionNever();

        sendFirstMessage();

//        verify(webSocketServer).startServer();
    }

    @Test
    public void preventConnectMessageBeingPassedToClient() {
        setupWebserverConnection();
        sendFirstMessage();

        String msg = CONNECT_PLEASE;
        TestObserver<String> testObserver = observeServerMessages();
        messageStream.onNext(msg);

        assertThat(testObserver.valueCount()).isEqualTo(0);
    }

    @Test
    public void willNotifyMessages() {
        setupWebserverConnection();
        sendFirstMessage();

        String msg = "Yo, server";
        TestObserver<String> testObserver = observeServerMessages();
        messageStream.onNext(msg);

        verifyReceivedMessage(testObserver, msg);
    }

    @Test
    public void willSubscribeToMessagesOnStartServer() {
        setupWebserverConnection();
        sendFirstMessage();

//        verify(pipe).receiveMessages();
    }

    @Test
    public void willStopOnWebSocketDisconnected() throws IOException {
        setupWebserverConnection();
        setupDisconnect();
        sendFirstMessage();

        TestObserver<String> testObserver = observeServerMessages();
        disconnectCompletable.onComplete();

        verifyStopAndComplete(testObserver);
    }

    @Test
    public void willDisconnectOnEndStream() throws IOException {
        setupWebserverConnection();
        sendFirstMessage();

        pipeChannelServer.sendEndStream();

        verify(pipe).close();
    }

    @Test
    public void canSendMessageToClient() throws IOException {
        setupWebserverConnection();
        sendFirstMessage();

        String msg = "Hello client, are you are ok?";

        pipeChannelServer.send(msg);

        verify(pipe).write(msg);
    }

    @Test
    public void checkWillHandleStartServerError() throws RemoteException {
        setupWebserverConnectionError();

        sendFirstMessage();

        verifySentMessage(2, MESSAGE_ERROR, new MessageException("websocketError", "Unable to setup websocket server: " + "Arg").toJson());
    }

    @Test
    public void willHandleOnDisconnectError() throws IOException {
        setupWebserverConnection();
        setupDisconnectError();
        sendFirstMessage();

        TestObserver<String> testObserver = observeServerMessages();

        verifyStopAndComplete(testObserver);
    }

    private void verifyStopAndComplete(TestObserver<String> testObserver) throws IOException {
        verify(pipe).close();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    private void verifySentMessage(int times, int type, String message) throws RemoteException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(replyToMessenger, times(times)).send(captor.capture());
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

    @NonNull
    private TestObserver<String> observeServerMessages() {
        return pipeChannelServer.subscribeToMessages().test();
    }

    private void verifyReceivedMessage(TestObserver<String> testObserver, String msg) {
        testObserver.assertNotComplete();
        testObserver.assertNoErrors();
        assertThat(testObserver.valueCount()).isEqualTo(1);
        assertThat(testObserver.values().get(0)).isEqualTo(msg);
    }

    private void sendFirstMessage() {
        Message m = setupMessage("Server, service me", "iClient");
        pipeChannelServer.handleMessage(m);
    }

    private void setupWebserverConnectionNever() {
//        when(webSocketServer.write(anyString())).thenReturn(Observable.<webSocketServer>never());
    }

    private void setupWebserverConnectionError() {
//        when(webSocketServer.write(anyString())).thenReturn(Observable.<webSocketServer>error(new Throwable("Arg")));
    }

    private void setupDisconnectError() {
//        when(pipe.onDisconnected()).thenReturn(Completable.error(new Throwable("You shall not disconnect")));
    }

    private void setupDisconnect() {
//        when(pipe.onDisconnected()).thenReturn(disconnectCompletable);
    }

    private void setupWebserverConnection() {
//        when(pipe.receiveMessages()).thenReturn(messageStream);
//        when(pipe.onDisconnected()).thenReturn(Completable.never());
        when(pipe.isConnected()).thenReturn(true);
//        when(pipe.write(anyString())).thenReturn();
    }

    @NonNull
    private Message setupMessage(String message, String clientId) {
        Message m = new Message();
        m.what = MESSAGE_REQUEST;
        m.replyTo = replyToMessenger;
        Bundle b = new Bundle();
        b.putString(KEY_DATA_REQUEST, message);
        b.putString(KEY_CHANNEL_TYPE, CHANNEL_WEBSOCKET);
        b.putString(KEY_CLIENT_ID, clientId);
        m.setData(b);
        return m;
    }

    class TestPipeChannelServer extends PipeChannelServer {

        TestPipeChannelServer(String serviceComponentName, String clientPackageName) {
            super(serviceComponentName, clientPackageName);
        }

        @Override
        protected Pipe createPipe(ParcelFileDescriptor descriptor) {
            return pipe;
        }
    }
}
