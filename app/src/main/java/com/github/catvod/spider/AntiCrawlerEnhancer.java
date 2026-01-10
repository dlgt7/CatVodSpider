package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.CookieManager;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.UA;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 深度优化版反爬虫增强器
 * 1. 自动管理 Referer 链
 * 2. 正态分布随机延迟（模拟人类行为）
 * 3. 注入 Sec-Fetch 系列现代浏览器指纹
 * 4. 自动同步 WebView 挑战后的 Cookie
 */
public class AntiCrawlerEnhancer {

    private final Random random = new Random();
    private final Map<String, String> refererMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequestTimeMap = new ConcurrentHashMap<>();
    private final Map<String, String> fingerprintMap = new ConcurrentHashMap<>();

    private boolean enableDelay = true;
    private boolean enableAutoReferer = true;

    private static class Loader {
        static volatile AntiCrawlerEnhancer INSTANCE = new AntiCrawlerEnhancer();
    }

    public static AntiCrawlerEnhancer get() {
        return Loader.INSTANCE;
    }

    /**
     * 初始化环境，确保 CookieManager 可用
     */
    public void init(Context context) {
        try {
            Context ctx = (context != null) ? context : Init.context();
            if (ctx == null) return;
            
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.flush();
            SpiderDebug.log("AntiCrawlerEnhancer: 运行环境已就绪");
        } catch (Throwable e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 正态分布随机延迟逻辑：相比简单随机，更难被 AI 识别为爬虫
     */
    private void checkDelay(String host) {
        if (!enableDelay) return;
        long lastTime = lastRequestTimeMap.getOrDefault(host, 0L);
        long now = System.currentTimeMillis();

        // 模拟正态分布：均值 1000ms，标准差 300ms
        long waitTime = (long) (random.nextGaussian() * 300 + 1000);
        waitTime = Math.max(400, Math.min(waitTime, 3000)); // 限制在 0.4s - 3s 之间

        long diff = now - lastTime;
        if (diff < waitTime) {
            try {
                Thread.sleep(waitTime - diff);
            } catch (InterruptedException ignored) {}
        }
        lastRequestTimeMap.put(host, System.currentTimeMillis());
    }

    /**
     * 构建高度仿真的动态请求头
     */
    private Map<String, String> makeHeaders(String url, Map<String, String> extHeaders) {
        Map<String, String> headers = new HashMap<>();
        String host = getHost(url);

        // 1. 基础仿真头
        headers.put("User-Agent", UA.getRandom()); // 动态获取项目定义的 UA
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

        // 2. 现代浏览器 Sec-Fetch 特征 (过防火墙关键)
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Upgrade-Insecure-Requests", "1");

        // 3. 自动 Referer 逻辑
        if (enableAutoReferer) {
            String prevUrl = refererMap.get(host);
            headers.put("Referer", TextUtils.isEmpty(prevUrl) ? (getProtocolHost(url) + "/") : prevUrl);
        }

        // 4. 指纹与设备伪装
        if (!fingerprintMap.containsKey(host)) {
            fingerprintMap.put(host, UUID.randomUUID().toString().substring(0, 8));
        }
        headers.put("X-Requested-With", "com.android.browser");
        headers.put("X-Device-Fingerprint", fingerprintMap.get(host));

        // 5. 自动同步项目中的 Cookie 状态 (过盾核心)
        String cookie = CookieManager.getInstance().getCookie(url);
        if (!TextUtils.isEmpty(cookie)) {
            headers.put("Cookie", cookie);
        }

        // 6. 外部传入头具有最高优先级（允许覆盖默认逻辑）
        if (extHeaders != null) headers.putAll(extHeaders);

        return headers;
    }

    /**
     * 增强版 GET 请求
     */
    public String enhancedGet(String url, Map<String, String> headers) {
        String host = getHost(url);
        try {
            checkDelay(host);
            Map<String, String> finalHeaders = makeHeaders(url, headers);
            
            // 完美适配项目中 OkHttp.java 的调用方式
            String result = OkHttp.string(url, finalHeaders);
            
            if (!TextUtils.isEmpty(result)) {
                refererMap.put(host, url);
            }
            return result;
        } catch (Throwable e) {
            SpiderDebug.log("Enhanced GET Error: " + url + " -> " + e.getMessage());
            return "";
        }
    }

    private String getHost(String urlStr) {
        try { return new URL(urlStr).getHost(); } catch (Exception e) { return ""; }
    }

    private String getProtocolHost(String urlStr) {
        try {
            URL url = new URL(urlStr);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception e) { return urlStr; }
    }
}
