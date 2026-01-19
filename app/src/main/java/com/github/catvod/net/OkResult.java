package com.github.catvod.net;

import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class OkResult {

    private int code;
    private String body;
    private Map<String, List<String>> headers;

    public OkResult() {
        this.code = -1;
        this.body = "";
        this.headers = null;
    }

    public OkResult(int code, String body, Map<String, List<String>> headers) {
        this.code = code;
        this.body = body;
        this.headers = headers;
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * 获取响应体为字节数组
     * @return 字节数组
     */
    public byte[] getBodyAsBytes() {
        return body != null ? body.getBytes() : new byte[0];
    }

    /**
     * 将响应体转换为指定类型的对象
     * @param classOfT 目标类型
     * @param <T> 泛型
     * @return 指定类型的对象
     */
    public <T> T getAsObject(Class<T> classOfT) {
        if (body == null || body.isEmpty()) {
            SpiderDebug.log("Response body is null or empty, returning null object");
            return null;
        }

        // 检查是否为JSON格式
        String trimmedBody = body.trim();
        if (!trimmedBody.startsWith("{") && !trimmedBody.startsWith("[")) {
            SpiderDebug.log("Response body is not JSON format, actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }

        try {
            return new Gson().fromJson(body, classOfT);
        } catch (JsonSyntaxException e) {
            SpiderDebug.log("Failed to parse JSON: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        } catch (Exception e) {
            SpiderDebug.log("Unexpected error during JSON parsing: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }
    }

    /**
     * 将响应体转换为指定类型的对象（使用TypeToken）
     * @param type 目标类型
     * @return 指定类型的对象
     */
    public <T> T getAsObjectType(Type type) {
        if (body == null || body.isEmpty()) {
            SpiderDebug.log("Response body is null or empty, returning null object");
            return null;
        }

        // 检查是否为JSON格式
        String trimmedBody = body.trim();
        if (!trimmedBody.startsWith("{") && !trimmedBody.startsWith("[")) {
            SpiderDebug.log("Response body is not JSON format, actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }

        try {
            return new Gson().fromJson(body, type);
        } catch (JsonSyntaxException e) {
            SpiderDebug.log("Failed to parse JSON: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        } catch (Exception e) {
            SpiderDebug.log("Unexpected error during JSON parsing: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }
    }

    /**
     * 将响应体转换为Map
     * @return Map对象
     */
    public Map<String, Object> getAsMap() {
        if (body == null || body.isEmpty()) {
            SpiderDebug.log("Response body is null or empty, returning empty map");
            return null;
        }

        // 检查是否为JSON格式
        String trimmedBody = body.trim();
        if (!trimmedBody.startsWith("{")) {
            SpiderDebug.log("Response body is not JSON object format, actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }

        try {
            return new Gson().fromJson(body, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (JsonSyntaxException e) {
            SpiderDebug.log("Failed to parse JSON as Map: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        } catch (Exception e) {
            SpiderDebug.log("Unexpected error during JSON parsing as Map: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }
    }

    /**
     * 将响应体转换为List
     * @return List对象
     */
    public List<Object> getAsList() {
        if (body == null || body.isEmpty()) {
            SpiderDebug.log("Response body is null or empty, returning empty list");
            return null;
        }

        // 检查是否为JSON格式
        String trimmedBody = body.trim();
        if (!trimmedBody.startsWith("[")) {
            SpiderDebug.log("Response body is not JSON array format, actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }

        try {
            return new Gson().fromJson(body, new TypeToken<List<Object>>(){}.getType());
        } catch (JsonSyntaxException e) {
            SpiderDebug.log("Failed to parse JSON as List: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        } catch (Exception e) {
            SpiderDebug.log("Unexpected error during JSON parsing as List: " + e.getMessage());
            SpiderDebug.log("Actual content (first 200 chars): " + 
                trimmedBody.substring(0, Math.min(200, trimmedBody.length())));
            return null;
        }
    }

    /**
     * 深拷贝方法
     */
    public OkResult clone() {
        OkResult cloned = new OkResult(code, body, null);
        if (headers != null) {
            cloned.headers = new java.util.HashMap<>(headers);
        }
        return cloned;
    }

    @Override
    public String toString() {
        return "OkResult{" +
                "code=" + code +
                ", bodyLength=" + (body != null ? body.length() : 0) +
                ", headersSize=" + (headers != null ? headers.size() : 0) +
                '}';
    }
}