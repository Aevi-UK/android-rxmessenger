package com.aevi.android.rxmessenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.google.gson.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;

public final class JsonConverter {

    public static String serialize(Jsonable object) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Bitmap.class, new BitmapSerialiser())
                .create();
        return gson.toJson(object);
    }

    public static <T extends Jsonable> T deserialize(String json, Class<T> type) throws JsonParseException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(JsonOption.class, new ExtrasDeserialiser())
                .registerTypeAdapter(Bitmap.class, new BitmapDeserialiser())
                .create();
        return type.cast(gson.fromJson(json, type));
    }

    private static class BitmapSerialiser implements JsonSerializer<Bitmap> {

        @Override
        public JsonElement serialize(Bitmap src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(writeBitmap(src));
        }
    }

    private static class BitmapDeserialiser implements JsonDeserializer<Bitmap> {

        @Override
        public Bitmap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return readBitmap(json.getAsString());
        }
    }

    private static class ExtrasDeserialiser implements JsonDeserializer<JsonOption> {

        public JsonOption deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            JsonObject obj = json.getAsJsonObject();
            JsonElement entry = obj.get("value");
            String className = obj.get("type").getAsString();
            try {
                Class clazz = Class.forName(className);
                return new JsonOption(context.deserialize(entry, clazz));
            } catch (ClassNotFoundException e) {
                return new JsonOption(entry, className);
            }
        }
    }

    private static String writeBitmap(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
    }

    private static Bitmap readBitmap(String in) {
        byte[] pngBytes = Base64.decode(in.getBytes(), Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
    }
}
