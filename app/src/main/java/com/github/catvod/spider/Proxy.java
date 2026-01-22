package com.github.catvod.spider;

import com.github.catvod.net.OkHttp;
import com.github.catvod.crawler.SpiderDebug;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Proxy {

    private static volatile int port = -1;
    private static volatile String host = "http://127.0.0.1";

    public static Object[] proxy(Map<String, String> params) {
        try {
            if (params != null && "ck".equals(params.get("do"))) {
                return new Object[]{200, "text/plain; charset=utf-8", 
                    new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8))};
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    public static void init() {
        if (port > 0) return;
        
        // 1. 扩充 2025 常见类名路径
        String[] possibleClasses = {
            "com.github.catvod.Proxy",
            "com.github.catvod.ProxyServer",
            "com.catvod.Proxy",
            "com.fongmi.android.tv.utils.Proxy", 
            "tv.fongmi.android.Proxy",
            "com.github.catvod.server.Proxy"
        };

        for (String clzName : possibleClasses) {
            try {
                Class<?> clz = Class.forName(clzName);
                port = (int) clz.getMethod("getPort").invoke(null);
                if (port > 0) {
                    checkHost(); // 确定端口后，探测是 IPv4 还是 IPv6
                    SpiderDebug.log("识别代理: " + host + ":" + port + " (" + clzName + ")");
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // 2. 如果反射失败，启动快速探测
        findPort();
    }

    /**
     * 探测当前环境支持的 Loopback 地址 (IPv4 vs IPv6)
     */
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
        if (port <= 0) {
            init();
            if (port <= 0) return null; // 兜底：返回 null 防止上游拼接错误 URL
        }
        String connector = (param == null || param.isEmpty() || param.startsWith("?") || param.startsWith("&")) ? "" : "?";
        return host + ":" + port + "/proxy?do=csp&siteKey=" + siteKey + connector + (param != null ? param : "");
    }

    public static String getUrl() {
        return port > 0 ? host + ":" + port + "/proxy" : null;
    }

    private static void findPort() {
        // 优先探测已知壳子的默认端口，加快启动速度
        int[] commonPorts = {9978, 8964, 10001, 19964};
        for (int p : commonPorts) {
            if (tryPort(p)) return;
        }
        // 范围扫描 (缩小范围至 100 个常用端口)
        for (int p = 8964; p < 9064; p++) {
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
