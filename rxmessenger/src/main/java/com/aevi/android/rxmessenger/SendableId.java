package com.aevi.android.rxmessenger;

import java.util.UUID;

public class SendableId implements Sendable {

    private String id;

    public SendableId() {
        this.id = UUID.randomUUID().toString();
    }

    protected SendableId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String toJson() {
        return JsonConverter.serialize(this);
    }

    public static SendableId fromJson(String json) {
        return JsonConverter.deserialize(json, SendableId.class);
    }
}
