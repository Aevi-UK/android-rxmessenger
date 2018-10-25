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
package com.aevi.android.rxmessenger.model;

/**
 * POJO passed between client and server to provide websocket host address and port
 */
public class ConnectionParams {

    private final String hostAddress;
    private final int port;

    public ConnectionParams(String hostAddress, int port) {
        this.hostAddress = hostAddress;
        this.port = port;
    }

    /**
     * @return The host address to connect to
     */
    public String getHostAddress() {
        return hostAddress;
    }

    /**
     * @return The port to connect to
     */
    public int getPort() {
        return port;
    }
}
