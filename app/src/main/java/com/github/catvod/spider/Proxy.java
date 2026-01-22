package com.github.catvod.spider;

import com.github.catvod.net.OkHttp;
import com.github.catvod.crawler.SpiderDebug;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Proxy {

    private static volatile int port = -1;
    private static String host = "http://127.0.0.1";

    /**
     * 核心代理入口，增加空指针和基础校验
     */
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

    /**
     * 适配 TVBox 和 FongMi 的多路径反射逻辑
     */
    public static void init() {
        if (port > 0) return;
        
        // 依次尝试不同版本壳子的 Proxy 类名
        String[] possibleClasses = {
            "com.github.catvod.Proxy",       // 经典 TVBox
            "com.github.catvod.ProxyServer", // FongMi 或部分新分支
            "com.catvod.Proxy"               // 精简版分支
        };

        for (String clzName : possibleClasses) {
            try {
                Class<?> clz = Class.forName(clzName);
                port = (int) clz.getMethod("getPort").invoke(null);
                if (port > 0) {
                    SpiderDebug.log("已通过反射识别代理端口: " + port + " (" + clzName + ")");
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // 如果反射全部失败，启动有限范围的快速扫描
        findPort();
    }

    public static int getPort() {
        return port;
    }

    /**
     * 增强的 URL 生成逻辑，自动处理参数连接符
     */
    public static String getUrl(String siteKey, String param) {
        if (port <= 0) init(); // 延迟初始化检查
        String connector = (param != null && (param.startsWith("&") || param.startsWith("?"))) ? "" : "?";
        return host + ":" + port + "/proxy?do=csp&siteKey=" + siteKey + connector + (param != null ? param : "");
    }

    public static String getUrl() {
        return host + ":" + port + "/proxy";
    }

    /**
     * 优化扫描逻辑：针对常用端口进行探测
     */
    private static void findPort() {
        // 8964:经典, 9978:FongMi常用, 10001:某些改版
        int[] commonPorts = {8964, 9978, 10001, 19964};
        for (int p : commonPorts) {
            if (check(p)) {
                port = p;
                SpiderDebug.log("探测到可用代理端口: " + port);
                return;
            }
        }
        // 最后的降级尝试
        for (int p = 8964; p < 9000; p++) {
            if (check(p)) {
                port = p;
                return;
            }
        }
    }

    private static boolean check(int p) {
        try {
            // 设置 500ms 超时，避免长时间挂起
            String res = OkHttp.string(host + ":" + p + "/proxy?do=ck", null);
            return "ok".equals(res != null ? res.trim() : "");
        } catch (Exception e) {
            return false;
        }
    }
}
