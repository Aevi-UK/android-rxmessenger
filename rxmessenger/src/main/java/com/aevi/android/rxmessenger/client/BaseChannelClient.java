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

package com.aevi.android.rxmessenger.client;

import android.content.ComponentName;
import android.content.Context;

/**
 * Base class that {@link com.aevi.android.rxmessenger.ChannelClient} can use to hold some common values
 */
public abstract class BaseChannelClient {

    protected final Context context;
    final ComponentName serviceComponentName;

    BaseChannelClient(Context context, ComponentName componentName) {
        this.context = context;
        this.serviceComponentName = componentName;
    }
}
