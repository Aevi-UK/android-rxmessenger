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

import android.content.ComponentName;
import android.content.Context;

import com.aevi.android.rxmessenger.client.ObservableMessengerClient;
import com.aevi.android.rxmessenger.client.ObservableWebSocketClient;

/**
 * Factory class that can be used to obtain any type of rx-messenger {@link ChannelClient}
 */
public final class Channels {

    /**
     * Obtain a messenger {@link ChannelClient}
     *
     * @param context       The Android context
     * @param componentName The name of the component to connect to
     * @return A {@link ChannelClient} that will communicate over Android Messenger
     */
    public static ChannelClient messenger(Context context, ComponentName componentName) {
        return new ObservableMessengerClient(context, componentName);
    }

    /**
     * Obtain a websocket {@link ChannelClient}
     *
     * @param context       The Android context
     * @param componentName The name of the component to connect to
     * @return A {@link ChannelClient} that will communicate over Android Messenger initially to setup a websocket and then use that for all messages
     */
    public static ChannelClient webSocket(Context context, ComponentName componentName) {
        return new ObservableWebSocketClient(context, componentName);
    }
}
