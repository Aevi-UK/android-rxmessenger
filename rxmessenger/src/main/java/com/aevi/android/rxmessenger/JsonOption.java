package com.aevi.android.rxmessenger;

public class JsonOption implements Jsonable {

    private final Object value;
    private final String type;

    public JsonOption(Object value) {
        this.value = value;
        this.type = value.getClass().getName();
    }

    protected JsonOption(Object value, String forceType) {
        this.value = value;
        this.type = forceType;
    }

    public Object getValue() {
        return value;
    }

    public String getType() {
        return type;
    }

    public String toJson() {
        return JsonConverter.serialize(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JsonOption that = (JsonOption) o;

        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }
        return type != null ? type.equals(that.type) : that.type == null;

    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
}
