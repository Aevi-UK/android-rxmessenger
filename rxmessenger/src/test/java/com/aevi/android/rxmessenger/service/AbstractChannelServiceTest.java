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
package com.aevi.android.rxmessenger.service;

import android.content.Intent;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.NonNull;

import com.aevi.android.rxmessenger.ChannelServer;
import com.aevi.android.rxmessenger.MockShadowMessenger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLog;

import static com.aevi.android.rxmessenger.MessageConstants.CHANNEL_MESSENGER;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class AbstractChannelServiceTest {

    TestAbstractChannelService testAbstractChannelService;

    @Mock
    ChannelServer channelServer;

    @Before
    public void setup() {
        ShadowLog.stream = System.out;
        initMocks(this);
        MockShadowMessenger.clearMessages();
        testAbstractChannelService = new TestAbstractChannelService(channelServer);
    }

    @Test
    public void willSetupChannelOnFirstMessageIntent() {
        Intent intent = new Intent();
        testAbstractChannelService.onBind(intent);
        assertThat(testAbstractChannelService.incomingHandler).isNotNull();

        testAbstractChannelService.incomingHandler.handleMessage(setupEmptyMessage());

        assertThat(testAbstractChannelService.channelServerMap).hasSize(1);
        assertThat(testAbstractChannelService.channelType).isEqualTo(CHANNEL_MESSENGER);
    }

    class TestAbstractChannelService extends AbstractChannelService {

        String channelType;

        ChannelServer channelServer;
        ChannelServer fakeHandler;
        String packageName;

        protected TestAbstractChannelService(ChannelServer fakeHandler) {
            attachBaseContext(RuntimeEnvironment.application);
            this.fakeHandler = fakeHandler;
        }

        @NonNull
        @Override
        protected Messenger getMessenger() {
            return mock(Messenger.class);
        }

        @Override
        protected void monitorForDeath(Messenger incomingMessenger, ChannelServer channelServer) {

        }

        @NonNull
        @Override
        protected ChannelServer getChannelServer(String clientId, String channelType) {
            this.channelType = channelType;
            return fakeHandler;
        }

        @Override
        protected void onNewClient(ChannelServer channelServer, String callingPackageName) {
            this.channelServer = channelServer;
            this.packageName = callingPackageName;
        }

        @Override
        public String getCallingPackage() {
            return "com.test.call.me.baby";
        }
    }

    @NonNull
    private Message setupEmptyMessage() {
        return mock(Message.class);
    }
}
