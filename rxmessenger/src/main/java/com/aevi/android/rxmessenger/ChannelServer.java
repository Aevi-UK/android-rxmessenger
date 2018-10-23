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

import android.os.IBinder;
import android.os.Message;

import io.reactivex.Observable;

/**
 * All message servers supported by this library should implement this interface
 */
public interface ChannelServer extends IBinder.DeathRecipient {

    /**
     * Send a message to the client
     *
     * @param message The message to send
     * @return True if the message was successfully sent
     */
    boolean send(String message);

    /**
     * Send end of stream message back to the client and close the stream
     *
     * @return True if the end message was sent successfully
     */
    boolean sendEndStream();

    /**
     * Send an exception to the client
     *
     * @param e The exception to send
     * @return True if the exception was successfully sent
     */
    boolean send(MessageException e);

    /**
     * Returns the last client message sent to this channel
     * <p>
     * <strong>WARNING: This method is blocking and will wait until there is at least one message to return</strong>
     * </p>
     *
     * @return The last message
     */
    String getLastMessage();

    /**
     * Allows a user of this channel to subscribe to client messages
     *
     * @return An observable stream of client messages
     */
    Observable<String> subscribeToMessages();

    /**
     * Called by the hosting service when the client is unbound
     */
    void clientDispose();

    /**
     * Called to close the connection with the client
     */
    void clientClose();

    /**
     * Allows an observer of this client to listen for dispose and close events
     *
     * @param clientListener A listener to listen for the terminal events
     */
    void addClientListener(ClientListener clientListener);

    /**
     * Removes a client listener from this channel
     *
     * @param clientListener The listener to remove
     */
    void removeClientListener(ClientListener clientListener);

    /**
     * Method called by server when a message is received
     *
     * @param msg The message to handle
     */
    void handleMessage(Message msg);

    interface ClientListener {

        /**
         * This is called when the client has been disposed of or destroyed on the client side
         */
        void onClientDispose();

        /**
         * This is called when the client is being disposed of by this service because of an ending message being sent
         */
        void onClientClosed();
    }
}
