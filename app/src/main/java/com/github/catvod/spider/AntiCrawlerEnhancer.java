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
 * 完美优化版反爬虫增强器
 * 1. 自动管理 Referer 链（解决图片/视频防盗链核心问题）
 * 2. 智能随机延迟（防 IP 频率屏蔽）
 * 3. 动态指纹与 UA 注入
 * 4. 适配项目 Init, OkHttp, UA 现有逻辑
 */
public class AntiCrawlerEnhancer {

    private final Random random = new Random();
    
    // 缓存每个域名的最后访问 URL，用于自动生成 Referer
    private final Map<String, String> refererMap = new ConcurrentHashMap<>();
    // 缓存每个域名的访问时间戳，用于频率控制
    private final Map<String, Long> lastRequestTimeMap = new ConcurrentHashMap<>();
    // 缓存设备指纹
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
     * 初始化环境
     */
    public void init(Context context) {
        try {
            Context ctx = (context != null) ? context : Init.context();
            if (ctx == null) return;
            
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            // 移除过时的 setAcceptThirdPartyCookies 调用，保持兼容性
            cookieManager.flush();
            SpiderDebug.log("AntiCrawlerEnhancer: 运行环境就绪");
        } catch (Throwable e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 智能延迟逻辑
     */
    private void checkDelay(String host) {
        if (!enableDelay) return;
        long lastTime = lastRequestTimeMap.getOrDefault(host, 0L);
        long now = System.currentTimeMillis();
        long diff = now - lastTime;

        // 随机设定 600ms - 1500ms 的保护间隔，模拟真人浏览
        long waitTime = 600 + random.nextInt(900);
        if (diff < waitTime) {
            try {
                Thread.sleep(waitTime - diff);
            } catch (InterruptedException ignored) {}
        }
        lastRequestTimeMap.put(host, System.currentTimeMillis());
    }

    /**
     * 构建动态请求头
     */
    private Map<String, String> makeHeaders(String url, Map<String, String> extHeaders) {
        Map<String, String> headers = new HashMap<>();
        // 先放入用户传入的自定义头
        if (extHeaders != null) headers.putAll(extHeaders);

        String host = getHost(url);

        // 1. 注入动态 User-Agent (使用项目中 UA 类的能力)
        if (!headers.containsKey("User-Agent")) {
            headers.put("User-Agent", UA.getRandom());
        }

        // 2. 自动注入 Referer (防盗链逻辑)
        if (enableAutoReferer && !headers.containsKey("Referer")) {
            String prevUrl = refererMap.get(host);
            headers.put("Referer", TextUtils.isEmpty(prevUrl) ? (getProtocolHost(url) + "/") : prevUrl);
        }

        // 3. 模拟指纹 (部分 WAF 防火墙会校验此特征)
        if (!fingerprintMap.containsKey(host)) {
            fingerprintMap.put(host, UUID.randomUUID().toString().substring(0, 8));
        }
        headers.put("X-Requested-With", "com.android.browser");
        headers.put("X-Device-Fingerprint", fingerprintMap.get(host));

        // 4. 标准浏览器行为补充
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

        return headers;
    }

    /**
     * 核心增强抓取：GET
     */
    public String enhancedGet(String url, Map<String, String> headers) {
        String host = getHost(url);
        try {
            checkDelay(host);
            Map<String, String> finalHeaders = makeHeaders(url, headers);
            
            // 使用 OkHttp.java 的 string(url, headers)
            String result = OkHttp.string(url, finalHeaders);
            
            if (!TextUtils.isEmpty(result)) {
                // 只有请求成功才更新 Referer 链
                refererMap.put(host, url);
            }
            return result;
        } catch (Throwable e) {
            SpiderDebug.log("Enhanced GET Error: " + url + " -> " + e.getMessage());
            return "";
        }
    }

    /**
     * 辅助：获取主机名
     */
    private String getHost(String urlStr) {
        try {
            return new URL(urlStr).getHost();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 辅助：获取协议+主机 (如 https://www.google.com)
     */
    private String getProtocolHost(String urlStr) {
        try {
            URL url = new URL(urlStr);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception e) {
            return urlStr;
        }
    }

    // --- 配置器 ---
    public AntiCrawlerEnhancer setEnableDelay(boolean enable) {
        this.enableDelay = enable;
        return this;
    }

    public AntiCrawlerEnhancer setEnableAutoReferer(boolean enable) {
        this.enableAutoReferer = enable;
        return this;
    }
}
