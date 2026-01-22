package com.github.catvod.spider;

import com.github.catvod.net.OkHttp;
import com.github.catvod.crawler.SpiderDebug;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Proxy {

    private static volatile int port = -1;
    private static volatile String host = "http://127.0.0.1";
    private static volatile String baseUrlPrefix = null;

    private static void log(Object msg) {
        try {
            SpiderDebug.log(msg.toString());
        } catch (Throwable ignored) {
            Log.d("Proxy", msg.toString()); // 极简壳子 fallback
        }
    }

    public static Object[] proxy(Map<String, String> params) {
        try {
            if (params != null && "ck".equals(params.get("do"))) {
                return new Object[]{200, "text/plain; charset=utf-8", 
                    new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8))};
            }
        } catch (Exception e) {
            log(e);
        }
        return null;
    }

    public static void init() {
        if (port > 0) return;
        
        // 2026 高频变体路径补全
        String[] possibleClasses = {
            "com.fongmi.android.tv.utils.Proxy", // FongMi 最新版
            "com.github.catvod.Proxy",           // 经典 TVBox
            "com.github.catvod.ProxyServer",     // 改版分支
            "tv.fongmi.android.Proxy",           // 某些特定分支
            "com.github.catvod.server.Proxy",    // Server 独立版
            "com.catvod.Proxy"
        };

        for (String clzName : possibleClasses) {
            try {
                Class<?> clz = Class.forName(clzName);
                port = (int) clz.getMethod("getPort").invoke(null);
                if (port > 0) {
                    checkHost(); // 验证是 127.0.0.1 还是 [::1]
                    log("已通过反射识别代理: " + getBasePrefix() + " (" + clzName + ")");
                    return;
                }
            } catch (Throwable ignored) {}
        }

        findPort();
    }

    private static String getBasePrefix() {
        if (baseUrlPrefix != null) return baseUrlPrefix;
        if (port <= 0) return null;
        baseUrlPrefix = host + ":" + port;
        return baseUrlPrefix;
    }

    private static void checkHost() {
        String[] hosts = {"http://127.0.0.1", "http://[::1]"};
        for (String h : hosts) {
            try {
                String res = OkHttp.string(h + ":" + port + "/proxy?do=ck", null);
                if ("ok".equals(res != null ? res.trim() : "")) {
                    host = h;
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    public static String getUrl(String siteKey, String param) {
        if (port <= 0) init();
        String prefix = getBasePrefix();
        if (prefix == null) {
            log("警告：代理端口不可用，无法生成链接");
            return null; 
        }
        String connector = (param == null || param.isEmpty() || param.startsWith("?") || param.startsWith("&")) ? "" : "?";
        return prefix + "/proxy?do=csp&siteKey=" + siteKey + connector + (param != null ? param : "");
    }

    public static String getUrl() {
        if (port <= 0) init();
        String prefix = getBasePrefix();
        return prefix != null ? prefix + "/proxy" : null;
    }

    public static int getPort() {
        return port;
    }

    private static void findPort() {
        // 9978 优先级最高 (FongMi 2025-2026 默认端口)
        int[] commonPorts = {9978, 8964, 10001, 19964, 18964};
        for (int p : commonPorts) {
            if (tryPort(p)) return;
        }
    }

    private static boolean tryPort(int p) {
        String[] hosts = {"http://127.0.0.1", "http://[::1]"};
        for (String h : hosts) {
            try {
                String res = OkHttp.string(h + ":" + p + "/proxy?do=ck", null);
                if ("ok".equals(res != null ? res.trim() : "")) {
                    port = p;
                    host = h;
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}
