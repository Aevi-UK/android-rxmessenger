package com.aevi.android.rxmessenger.model;

/**
 * Internal use only
 *
 * These details are deliberately public as this store and password is only present to ensure that
 * websocket based rx-messenger messages can be sent over a secure socket. The password/key details are public for now
 *
 * Future versions of this library will allow users to pass in the SSL cert and key
 */
public interface KeystoreCredentials {
    String KEYSTORE_FILENAME = "/keystore.jks";
    char[] KEYSTORE_PASS = "rxmess".toCharArray();
}
