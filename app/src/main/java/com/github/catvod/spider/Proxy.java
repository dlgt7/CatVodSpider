package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Proxy {

    private static Method method;
    private static int port;
    private static String host;
    private static boolean enabled;

    public static Object[] proxy(Map<String, String> params) {
        if (params == null) return null;
        
        String action = params.get("do");
        if (TextUtils.isEmpty(action)) return null;
        
        switch (action) {
            case "ck":
                return check();
            case "file":
                return file(params);
            case "text":
                return text(params);
            case "json":
                return json(params);
            case "image":
                return image(params);
            case "video":
                return video(params);
            case "audio":
                return audio(params);
            default:
                return null;
        }
    }

    private static Object[] check() {
        return new Object[]{200, "text/plain; charset=utf-8", new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8))};
    }

    private static Object[] file(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error("Path is empty");
        
        File file = new File(path);
        if (!file.exists()) return error("File not found: " + path);
        if (!file.canRead()) return error("File not readable: " + path);
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error("File not found: " + path);
        }
    }

    private static Object[] text(Map<String, String> params) {
        String content = params.get("content");
        if (TextUtils.isEmpty(content)) content = "";
        return new Object[]{200, "text/plain; charset=utf-8", new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))};
    }

    private static Object[] json(Map<String, String> params) {
        String content = params.get("content");
        if (TextUtils.isEmpty(content)) content = "{}";
        return new Object[]{200, "application/json; charset=utf-8", new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))};
    }

    private static Object[] image(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error("Path is empty");
        
        File file = new File(path);
        if (!file.exists()) return error("Image not found: " + path);
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            if (!mimeType.startsWith("image/")) mimeType = "image/jpeg";
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error("Image not found: " + path);
        }
    }

    private static Object[] video(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error("Path is empty");
        
        File file = new File(path);
        if (!file.exists()) return error("Video not found: " + path);
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            if (!mimeType.startsWith("video/")) mimeType = "video/mp4";
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error("Video not found: " + path);
        }
    }

    private static Object[] audio(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return error("Path is empty");
        
        File file = new File(path);
        if (!file.exists()) return error("Audio not found: " + path);
        
        try {
            InputStream is = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            if (!mimeType.startsWith("audio/")) mimeType = "audio/mpeg";
            return new Object[]{200, mimeType, is};
        } catch (FileNotFoundException e) {
            return error("Audio not found: " + path);
        }
    }

    private static Object[] error(String message) {
        return new Object[]{404, "text/plain; charset=utf-8", new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8))};
    }

    public static void init() {
        try {
            Class<?> clz = Class.forName("com.github.catvod.Proxy");
            port = (int) clz.getMethod("getPort").invoke(null);
            method = clz.getMethod("getUrl", boolean.class);
            enabled = true;
            SpiderDebug.log("本地代理端口: " + port);
        } catch (Throwable e) {
            findPort();
        }
    }

    public static int getPort() {
        return port;
    }

    public static String getHost() {
        return host != null ? host : "127.0.0.1";
    }

    public static void setHost(String h) {
        host = h;
    }

    public static boolean isEnabled() {
        return enabled && port > 0;
    }

    public static String getUrl(String siteKey, String param) {
        return "proxy://do=csp&siteKey=" + siteKey + param;
    }

    public static String getUrl() {
        return getUrl(true);
    }

    public static String getUrl(boolean local) {
        try {
            return (String) method.invoke(null, local);
        } catch (Throwable e) {
            return "http://127.0.0.1:" + port + "/proxy";
        }
    }

    public static String getUrl(int port) {
        return "http://127.0.0.1:" + port + "/proxy";
    }

    public static String getUrl(String host, int port) {
        return "http://" + host + ":" + port + "/proxy";
    }

    public static String buildUrl(String action, Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(getUrl()).append("?do=").append(action);
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append("&").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private static void findPort() {
        if (port > 0) return;
        for (int p = 8964; p < 9999; p++) {
            if ("ok".equals(OkHttp.string("http://127.0.0.1:" + p + "/proxy?do=ck", null))) {
                SpiderDebug.log("本地代理端口: " + p);
                port = p;
                enabled = true;
                break;
            }
        }
        if (port == 0) {
            enabled = false;
            SpiderDebug.log("未找到本地代理端口");
        }
    }

    private static String getMimeType(String filename) {
        if (TextUtils.isEmpty(filename)) return "application/octet-stream";
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase() : "";
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "svg":
                return "image/svg+xml";
            case "mp4":
                return "video/mp4";
            case "mkv":
                return "video/x-matroska";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "webm":
                return "video/webm";
            case "m3u8":
                return "application/x-mpegURL";
            case "ts":
                return "video/MP2T";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "aac":
                return "audio/aac";
            case "ogg":
                return "audio/ogg";
            case "m4a":
                return "audio/mp4";
            case "wma":
                return "audio/x-ms-wma";
            case "pdf":
                return "application/pdf";
            case "txt":
                return "text/plain";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "html":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "application/javascript";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            default:
                return "application/octet-stream";
        }
    }
}