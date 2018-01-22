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

public interface MessageConstants {

    int MESSAGE_REQUEST = 1;
    int MESSAGE_RESPONSE = 4;
    int MESSAGE_END_STREAM = 8;
    int MESSAGE_ERROR = 16;

    String KEY_CLIENT_ID = "clientId";
    String KEY_DATA_REQUEST = "dataRequest";
    String KEY_DATA_RESPONSE = "dataResponse";
    String KEY_DATA_SENDER = "sender";
}
