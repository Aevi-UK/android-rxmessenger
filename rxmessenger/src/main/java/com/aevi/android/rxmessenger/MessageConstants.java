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
