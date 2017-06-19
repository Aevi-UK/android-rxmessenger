package com.aevi.android.rxmessenger;

public class MessageException extends Throwable implements Jsonable {

    protected final String code;
    protected final String message;

    public MessageException(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
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

        MessageException that = (MessageException) o;

        if (code != null ? !code.equals(that.code) : that.code != null) {
            return false;
        }
        return message != null ? message.equals(that.message) : that.message == null;

    }

    @Override
    public int hashCode() {
        int result = code != null ? code.hashCode() : 0;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    public static MessageException fromJson(String json) {
        return JsonConverter.deserialize(json, MessageException.class);
    }
}
