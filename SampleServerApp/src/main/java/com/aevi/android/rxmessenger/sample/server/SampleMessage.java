package com.aevi.android.rxmessenger.sample.server;


public class SampleMessage {

    private final String messageType;
    private final String messageData;

    public SampleMessage(String messageType) {
        this.messageType = messageType;
        this.messageData = "";
    }

    public SampleMessage(String messageType, String messageData) {
        this.messageType = messageType;
        this.messageData = messageData;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessageData() {
        return messageData;
    }
}
