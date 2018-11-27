package com.aevi.android.rxmessenger.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;

class DataObject {

    transient final Gson gson = new GsonBuilder().create();

    private String id;

    public DataObject() {
        id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public String toJson() {
        return gson.toJson(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DataObject that = (DataObject) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}

