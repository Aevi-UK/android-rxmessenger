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

import com.aevi.android.rxmessenger.client.NoSuchServiceException;

import io.reactivex.Completable;
import io.reactivex.Observable;

/**
 * All message clients supported by this library should implement this client interface
 */
public interface ChannelClient {

    /**
     * Connect to the remote service using a new unique client id.
     * <p>
     * The connection will then be kept open until the remote end closes it or {@link #closeConnection()} is called on this instance.
     * <p>
     * Note that {@link #sendMessage(String)} will automatically connect if required to send a message.
     *
     * @return Completable that will complete on success and error on failure
     */
    Completable connect();

    /**
     * Returns whether we are connected to the service or not.
     *
     * @return True if connected, false otherwise.
     */
    boolean isConnected();

    /**
     * Used to send a message to an {@link ChannelServer} implementation and observe the responses from it.
     * <p>
     * This will connect to the service if not already connected when called.
     * <p>
     * The stream returned will only return messages from the point of subscription.
     * <p>
     * NOTE: The messages are only sent once a client is subscribed to the Observable.
     * <p>
     * NOTE: If the server end of this channel disconnects then a {@link java.util.NoSuchElementException} will be passed to the observable `onError`
     * method
     *
     * @param requestData The data to send (usually a serialised JSON object)
     * @return An Observable stream of Strings containing data that the service sends back to this client
     * @throws NoSuchServiceException Thrown if client cannot find the corresponding service to connect to
     */
    Observable<String> sendMessage(final String requestData);

    /**
     * Close the connection to the service.
     * <p>
     * This will complete the response stream returned from {@link #sendMessage(String)}.
     * <p>
     * Calling {@link #sendMessage(String)} after this point will create a new connection.
     */
    void closeConnection();
}
