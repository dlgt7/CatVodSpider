package com.github.catvod.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Json {

    private static final Gson gson = new Gson();

    public static JsonElement parse(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (Throwable e) {
            return new JsonParser().parse(json);
        }
    }

    public static JsonObject safeObject(String json) {
        try {
            JsonObject obj = parse(json).getAsJsonObject();
            return obj == null ? new JsonObject() : obj;
        } catch (Throwable e) {
            return new JsonObject();
        }
    }

    // 提供向后兼容的Gson访问方法
    public static Gson getGson() {
        return gson;
    }

    // 便捷的序列化和反序列化方法
    public static <T> T toObject(String json, Class<T> classOfT) {
        try {
            return getGson().fromJson(json, classOfT);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(Object src) {
        try {
            return getGson().toJson(src);
        } catch (Exception e) {
            return "";
        }
    }
}
