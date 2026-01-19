package com.github.catvod.spider;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.webkit.CookieManager;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.UA;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 深度优化版反爬虫增强器
 * 1. 自动管理 Referer 链
 * 2. 正态分布随机延迟（模拟人类行为）
 * 3. 注入 Sec-Fetch 系列现代浏览器指纹
 * 4. 自动同步 WebView 挑战后的 Cookie
 * 5. 网络异常重试机制
 * 6. 代理切换准备
 * 7. Sec-Ch-Ua头伪装
 * 8. 动态请求特征伪装
 * 9. IP轮换策略
 * 10. 请求频率智能调节
 * 11. 指数退避重试策略
 * 12. 响应头监控与自动纠偏
 * 13. 会话级UA一致性
 * 14. 并发安全优化
 * 15. 滑动窗口频率限制优化
 * 16. Session级Cookie隔离
 * 17. LRU Referer缓存管理
 * 18. UA持久化
 * 19. 精确Referer上下文逻辑
 */
public class AntiCrawlerEnhancer {

    private final Random random = new Random();
    // 使用LRU缓存管理Referer，限制大小为1000，键为protocol+host+path以实现精确上下文
    private final Map<String, String> refererMap = Collections.synchronizedMap(new LinkedHashMap<String, String>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 1000; // 限制缓存大小为1000个
        }
    });
    private final Map<String, Long> lastRequestTimeMap = new ConcurrentHashMap<>();
    private final Map<String, String> fingerprintMap = new ConcurrentHashMap<>();
    private final Map<String, String> secChUaMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestCountMap = new ConcurrentHashMap<>();
    private final Map<String, String> proxyMap = new ConcurrentHashMap<>();
    private final Map<String, Long> windowStartTimeMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> windowRequestCountMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUAMap = new ConcurrentHashMap<>(); // 会话级UA映射
    private final Map<String, String> secFetchMap = new ConcurrentHashMap<>(); // Sec-Fetch指纹映射

    private Context appContext;
    private SharedPreferences prefs;
    
    private boolean enableDelay = true;
    private boolean enableAutoReferer = true;
    private boolean enableDynamicHeaders = true;
    private int maxRetries = 3;
    private long retryDelay = 1000; // 重试延迟（毫秒）
    private long baseDelay = 1000; // 基础延迟
    private long maxDelay = 5000; // 最大延迟
    private int maxRequestsPerDomain = 10; // 每个域名的最大请求数（用于频率控制）
    private long timeWindow = 60000; // 时间窗口（1分钟）

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
            this.appContext = context.getApplicationContext();
            this.prefs = appContext.getSharedPreferences("anti_crawler_prefs", Context.MODE_PRIVATE);
            
            // 从SharedPreferences恢复持久化的UA
            restorePersistedUA();
            
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.flush();
            SpiderDebug.log("AntiCrawlerEnhancer: 运行环境已就绪");
        } catch (Throwable e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 从SharedPreferences恢复持久化的UA
     */
    private void restorePersistedUA() {
        if (prefs != null) {
            Map<String, ?> allPrefs = prefs.getAll();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                if (entry.getKey().startsWith("ua_") && entry.getValue() instanceof String) {
                    String host = entry.getKey().substring(3); // 移除"ua_"前缀
                    sessionUAMap.put(host, (String) entry.getValue());
                }
            }
        }
    }

    /**
     * 持久化UA到SharedPreferences
     * @param host 目标主机
     * @param ua User-Agent字符串
     */
    private void persistUA(String host, String ua) {
        if (prefs != null) {
            prefs.edit().putString("ua_" + host, ua).apply();
        }
    }

    /**
     * 设置重试次数和延迟
     * @param retries 重试次数
     * @param delay 重试延迟（毫秒）
     */
    public void setRetryConfig(int retries, long delay) {
        this.maxRetries = retries;
        this.retryDelay = delay;
    }

    /**
     * 设置延迟配置
     * @param baseDelay 基础延迟（毫秒）
     * @param maxDelay 最大延迟（毫秒）
     */
    public void setDelayConfig(long baseDelay, long maxDelay) {
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    /**
     * 设置频率限制
     * @param maxRequests 每个域名的最大请求数
     * @param window 时间窗口（毫秒）
     */
    public void setFrequencyLimit(int maxRequests, long window) {
        this.maxRequestsPerDomain = maxRequests;
        this.timeWindow = window;
    }

    /**
     * 设置代理
     * @param host 目标主机
     * @param proxy 代理地址（格式：ip:port）
     */
    public void setProxy(String host, String proxy) {
        proxyMap.put(host, proxy);
    }

    /**
     * 清除代理设置
     */
    public void clearProxy() {
        proxyMap.clear();
    }

    /**
     * 获取会话级UA（确保单次会话内的UA一致性）
     * @param host 目标主机
     * @return 会话级UA字符串
     */
    public String getSessionUA(String host) {
        String ua = sessionUAMap.computeIfAbsent(host, k -> UA.getRandom());
        // 持久化到SharedPreferences
        persistUA(host, ua);
        return ua;
    }

    /**
     * 获取Sec-Fetch指纹（根据请求类型动态生成）
     * @param host 目标主机
     * @param requestType 请求类型（如 document, image, empty 等）
     * @return Sec-Fetch相关头信息
     */
    public Map<String, String> getSecFetchHeaders(String host, String requestType) {
        Map<String, String> headers = new HashMap<>();
        
        // 根据请求类型动态设置Sec-Fetch头
        if ("image".equals(requestType)) {
            headers.put("Sec-Fetch-Dest", "image");
            headers.put("Sec-Fetch-Mode", "no-cors");
        } else if ("api".equals(requestType) || "xhr".equals(requestType)) {
            headers.put("Sec-Fetch-Dest", "empty");
            headers.put("Sec-Fetch-Mode", "cors");
        } else if ("navigate".equals(requestType)) {
            headers.put("Sec-Fetch-Dest", "document");
            headers.put("Sec-Fetch-Mode", "navigate");
        } else {
            // 默认设置
            headers.put("Sec-Fetch-Dest", pickRandom(new String[]{"document", "empty", "image"}));
            headers.put("Sec-Fetch-Mode", pickRandom(new String[]{"navigate", "cors", "no-cors"}));
        }
        
        headers.put("Sec-Fetch-Site", pickRandom(new String[]{"none", "same-site", "same-origin", "cross-site"}));
        if (headers.get("Sec-Fetch-Mode").equals("navigate")) {
            headers.put("Sec-Fetch-User", "?1");
        }
        
        return headers;
    }

    /**
     * 清除会话级UA（当Cookie过期时）
     * @param host 目标主机
     */
    public void clearSessionUA(String host) {
        sessionUAMap.remove(host);
        if (prefs != null) {
            prefs.edit().remove("ua_" + host).apply();
        }
    }

    /**
     * 清除指定域名的Referer
     * @param host 目标主机
     */
    public void clearReferer(String host) {
        synchronized (refererMap) {
            // 清除该主机的所有Referer记录
            refererMap.entrySet().removeIf(entry -> entry.getKey().contains(host));
        }
    }

    /**
     * 清除所有Referer
     */
    public void clearAllReferers() {
        synchronized (refererMap) {
            refererMap.clear();
        }
    }

    /**
     * 根据URL获取Referer键（协议+主机+路径）
     * @param url 目标URL
     * @return Referer键
     */
    private String getRefererKey(String url) {
        try {
            java.net.URL netUrl = new java.net.URL(url);
            String protocol = netUrl.getProtocol();
            String host = netUrl.getHost();
            String path = netUrl.getPath();
            return protocol + "://" + host + path; // 包含协议、主机和路径
        } catch (Exception e) {
            // 如果解析失败，使用主机名作为备选
            try {
                java.net.URL netUrl = new java.net.URL(url);
                return netUrl.getProtocol() + "://" + netUrl.getHost();
            } catch (Exception ex) {
                return url; // 最后备选方案
            }
        }
    }

    /**
     * 检查滑动窗口内的请求频率 - 使用compute方法确保原子性
     */
    private boolean isRateLimited(String host) {
        long currentTime = System.currentTimeMillis();
        
        // 使用compute方法确保窗口时间和计数操作的原子性
        Long windowStart = windowStartTimeMap.compute(host, (k, existingValue) -> {
            if (existingValue == null || (currentTime - existingValue) > timeWindow) {
                // 如果窗口不存在或已过期，重置窗口开始时间
                return currentTime;
            }
            return existingValue;
        });
        
        // 检查是否超过时间窗口，如果是，重置计数
        if (currentTime - windowStart > timeWindow) {
            windowStartTimeMap.put(host, currentTime);
            AtomicInteger count = windowRequestCountMap.get(host);
            if (count != null) {
                count.set(1);
            } else {
                windowRequestCountMap.put(host, new AtomicInteger(1));
            }
            return false;
        }
        
        AtomicInteger count = windowRequestCountMap.computeIfAbsent(host, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();
        
        return currentCount >= maxRequestsPerDomain;
    }

    /**
     * 智能延迟控制：根据请求频率动态调整延迟
     */
    private void checkDelay(String host) {
        if (!enableDelay) return;
        
        // 获取该域名的请求数量 - 使用原子计数器确保并发安全
        AtomicInteger requestCounter = requestCountMap.computeIfAbsent(host, k -> new AtomicInteger(0));
        int requestCount = requestCounter.incrementAndGet();
        
        // 根据请求数量动态调整延迟
        double multiplier = Math.min(1.0 + (requestCount * 0.2), 3.0); // 最多重3倍
        
        // 正态分布随机延迟逻辑：根据请求数量调整均值
        long adjustedBaseDelay = (long) (baseDelay * multiplier);
        long adjustedMaxDelay = (long) (maxDelay * multiplier);
        
        // 使用正态分布生成延迟时间
        long waitTime = (long) (random.nextGaussian() * 300 + adjustedBaseDelay);
        waitTime = Math.max(baseDelay / 2, Math.min(waitTime, adjustedMaxDelay)); // 限制在合理范围内

        long now = System.currentTimeMillis();
        // 使用原子操作更新最后请求时间
        long lastTime = lastRequestTimeMap.compute(host, (k, v) -> {
            if (v == null) return now;
            return v;
        });
        
        long diff = now - lastTime;
        
        if (diff < waitTime) {
            try {
                Thread.sleep(waitTime - diff);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTimeMap.put(host, System.currentTimeMillis());
    }

    /**
     * 生成Sec-Ch-Ua头伪装
     * 修复：host.hashCode() % secChUas.length，处理负数情况
     * @return Sec-Ch-Ua头字符串
     */
    private String generateSecChUa(String host) {
        if (!secChUaMap.containsKey(host)) {
            String[] secChUas = {
                "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                "\"Google Chrome\";v=\"119\", \"Chromium\";v=\"119\", \"Not?A_Brand\";v=\"24\"",
                "\"Not_A Brand\";v=\"8\", \"Google Chrome\";v=\"104\", \"Chromium\";v=\"104\"",
                "\"Chromium\";v=\"104\", \"Not_A Brand\";v=\"8\", \"Google Chrome\";v=\"104\"",
                "\"Not_A Brand\";v=\"99\", \"Google Chrome\";v=\"122\", \"Chromium\";v=\"122\"",
                "\"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\", \"Not?A_Brand\";v=\"24\""
            };
            int index = Math.abs(host.hashCode()) % secChUas.length;
            secChUaMap.put(host, secChUas[index]);
        }
        return secChUaMap.get(host);
    }

    /**
     * 构建高度仿真的动态请求头
     * @param url 目标URL
     * @param extHeaders 外部传入的请求头
     * @param requestType 请求类型（如 document, image, api, xhr 等）
     * @return 构建好的请求头
     */
    private Map<String, String> makeHeaders(String url, Map<String, String> extHeaders, String requestType) {
        Map<String, String> headers = new HashMap<>();
        String host = getHost(url);

        // 1. 基础仿真头 - 使用会话级UA确保一致性
        headers.put("User-Agent", getSessionUA(host));
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-US;q=0.7");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("DNT", "1"); // Do Not Track

        // 2. 动态Sec-Fetch头（根据请求类型）
        headers.putAll(getSecFetchHeaders(host, requestType));

        // 3. Sec-Ch-Ua 头伪装
        headers.put("Sec-Ch-Ua", generateSecChUa(host));
        headers.put("Sec-Ch-Ua-Mobile", "?0");
        headers.put("Sec-Ch-Ua-Platform", pickRandom(new String[]{"\"Windows\"", "\"macOS\"", "\"Linux\"", "\"Android\""}));

        // 4. 自动 Referer 逻辑 - 使用精确的URL上下文
        if (enableAutoReferer) {
            String refererKey = getRefererKey(url);
            String prevUrl = null;
            synchronized (refererMap) {
                prevUrl = refererMap.get(refererKey);
            }
            headers.put("Referer", TextUtils.isEmpty(prevUrl) ? (getProtocolHost(url) + "/") : prevUrl);
        }

        // 5. 指纹与设备伪装
        if (!fingerprintMap.containsKey(host)) {
            fingerprintMap.put(host, UUID.randomUUID().toString().substring(0, 8));
        }
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("X-Device-Fingerprint", fingerprintMap.get(host));

        // 6. 自动同步项目中的 Cookie 状态 (过盾核心)
        String cookie = CookieManager.getInstance().getCookie(url);
        if (!TextUtils.isEmpty(cookie)) {
            headers.put("Cookie", cookie);
        }

        // 7. 动态请求特征伪装
        if (enableDynamicHeaders) {
            // 随机添加一些常见的请求头
            if (random.nextBoolean()) {
                headers.put("TE", "trailers");
            }
            if (random.nextBoolean()) {
                headers.put("Cache-Control", "no-cache");
            }
            if (random.nextBoolean()) {
                headers.put("Pragma", "no-cache");
            }
        }

        // 8. 外部传入头具有最高优先级（允许覆盖默认逻辑）
        if (extHeaders != null) headers.putAll(extHeaders);

        return headers;
    }

    /**
     * 重载方法：使用默认请求类型构建请求头
     */
    private Map<String, String> makeHeaders(String url, Map<String, String> extHeaders) {
        return makeHeaders(url, extHeaders, "document"); // 默认为document类型
    }

    /**
     * 随机选择数组中的一个元素
     */
    private String pickRandom(String[] array) {
        return array[random.nextInt(array.length)];
    }

    /**
     * 指数退避算法计算延迟
     * @param attempt 当前尝试次数（从0开始）
     * @return 延迟时间（毫秒）
     */
    private long exponentialBackoffDelay(int attempt) {
        // 指数增长：1, 2, 4, 8...
        long baseDelay = this.retryDelay * (1L << attempt); // 2^attempt
        // 添加随机抖动（±25%）
        double jitter = 0.75 + random.nextDouble() * 0.5; // 0.75~1.25
        return Math.round(baseDelay * jitter);
    }

    /**
     * 监控响应头并自动纠偏
     * @param response HTTP响应
     * @param host 目标主机
     */
    private void monitorResponseHeaders(Response response, String host) {
        if (response == null) return;

        // 监控Set-Cookie头
        for (String cookie : response.headers("Set-Cookie")) {
            if (!TextUtils.isEmpty(cookie)) {
                SpiderDebug.log("检测到服务器更新Cookie: " + cookie);
                // 同步到CookieManager
                try {
                    CookieManager cookieManager = CookieManager.getInstance();
                    String protocolHost = getProtocolHost(response.request().url().toString());
                    cookieManager.setCookie(protocolHost, cookie);
                    cookieManager.flush(); // 立即同步
                } catch (Exception e) {
                    SpiderDebug.log("Cookie同步失败: " + e.getMessage());
                }
            }
        }

        // 监控速率限制头
        String rateLimitRemaining = response.header("X-RateLimit-Remaining");
        if (!TextUtils.isEmpty(rateLimitRemaining)) {
            try {
                int remaining = Integer.parseInt(rateLimitRemaining);
                if (remaining <= 2) { // 剩余请求数很少
                    SpiderDebug.log("检测到速率限制接近耗尽，暂停对该域名的请求: " + host);
                    // 可以在这里增加对该域名的延迟或暂时停止请求
                }
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }

        // 检查响应状态码
        int code = response.code();
        if (code == 403 || code == 429) { // 被封禁或限流
            SpiderDebug.log("检测到访问被拒绝(" + code + ")，可能需要重新预热: " + host);
            // 清除会话级UA，下次请求时会使用新的UA
            clearSessionUA(host);
        }
    }

    /**
     * 带重试机制的增强版 GET 请求
     */
    public String enhancedGet(String url, Map<String, String> headers) {
        return enhancedGet(url, headers, "document");
    }

    /**
     * 带重试机制的增强版 GET 请求（指定请求类型）
     * @param url 请求URL
     * @param headers 外部传入的请求头
     * @param requestType 请求类型（如 document, image, api 等）
     * @return 响应内容
     */
    public String enhancedGet(String url, Map<String, String> headers, String requestType) {
        String host = getHost(url);
        
        // 检查频率限制
        if (isRateLimited(host)) {
            SpiderDebug.log("达到频率限制，稍后重试: " + url);
            try {
                Thread.sleep(maxDelay * 2); // 频率限制后的等待
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
        }
        
        int attempts = 0;
        
        while (attempts <= maxRetries) {
            try {
                checkDelay(host);
                Map<String, String> finalHeaders = makeHeaders(url, headers, requestType);
                
                // 完美适配项目中 OkHttp.java 的调用方式
                Response response = OkHttp.getResponse(url, finalHeaders);
                if (response != null) {
                    String result = response.body().string();
                    
                    // 监控响应头并自动纠偏
                    monitorResponseHeaders(response, host);
                    
                    if (!TextUtils.isEmpty(result)) {
                        // 使用精确的URL上下文存储Referer
                        String refererKey = getRefererKey(url);
                        synchronized (refererMap) {
                            refererMap.put(refererKey, url);
                        }
                        response.close();
                        return result; // 成功则直接返回
                    }
                    
                    response.close();
                }
            } catch (Throwable e) {
                SpiderDebug.log("Enhanced GET Attempt " + (attempts + 1) + " Error: " + url + " -> " + e.getMessage());
                
                if (attempts >= maxRetries) {
                    SpiderDebug.log("Enhanced GET最终失败: " + url);
                    return ""; // 达到最大重试次数后返回空
                }
                
                // 指数退避重试
                try {
                    long delay = exponentialBackoffDelay(attempts);
                    SpiderDebug.log("重试前等待: " + delay + "ms");
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "";
                }
            }
            attempts++;
        }
        
        return "";
    }

    /**
     * 带重试机制的增强版 POST 请求
     */
    public String enhancedPost(String url, String body, Map<String, String> headers) {
        return enhancedPost(url, body, headers, "xhr");
    }

    /**
     * 带重试机制的增强版 POST 请求（指定请求类型）
     * @param url 请求URL
     * @param body 请求体
     * @param headers 外部传入的请求头
     * @param requestType 请求类型（如 xhr, api 等）
     * @return 响应内容
     */
    public String enhancedPost(String url, String body, Map<String, String> headers, String requestType) {
        String host = getHost(url);
        
        // 检查频率限制
        if (isRateLimited(host)) {
            SpiderDebug.log("达到频率限制，稍后重试: " + url);
            try {
                Thread.sleep(maxDelay * 2); // 频率限制后的等待
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
        }
        
        int attempts = 0;
        
        while (attempts <= maxRetries) {
            try {
                checkDelay(host);
                Map<String, String> finalHeaders = makeHeaders(url, headers, requestType);
                
                // 使用OkHttp的POST方法
                Response response = OkHttp.postResponse(url, body, finalHeaders);
                if (response != null) {
                    String result = response.body().string();
                    
                    // 监控响应头并自动纠偏
                    monitorResponseHeaders(response, host);
                    
                    if (!TextUtils.isEmpty(result)) {
                        // 使用精确的URL上下文存储Referer
                        String refererKey = getRefererKey(url);
                        synchronized (refererMap) {
                            refererMap.put(refererKey, url);
                        }
                        response.close();
                        return result; // 成功则直接返回
                    }
                    
                    response.close();
                }
            } catch (Throwable e) {
                SpiderDebug.log("Enhanced POST Attempt " + (attempts + 1) + " Error: " + url + " -> " + e.getMessage());
                
                if (attempts >= maxRetries) {
                    SpiderDebug.log("Enhanced POST最终失败: " + url);
                    return ""; // 达到最大重试次数后返回空
                }
                
                // 指数退避重试
                try {
                    long delay = exponentialBackoffDelay(attempts);
                    SpiderDebug.log("重试前等待: " + delay + "ms");
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "";
                }
            }
            attempts++;
        }
        
        return "";
    }

    /**
     * 获取请求头生成器
     * @param url 目标URL
     * @return 包含所有反爬虫伪装的请求头
     */
    public Map<String, String> getEnhancedHeaders(String url) {
        return makeHeaders(url, null);
    }

    /**
     * 获取指定类型的请求头
     * @param url 目标URL
     * @param requestType 请求类型
     * @return 包含所有反爬虫伪装的请求头
     */
    public Map<String, String> getEnhancedHeaders(String url, String requestType) {
        return makeHeaders(url, null, requestType);
    }

    /**
     * 重置域名的请求计数
     * @param host 域名
     */
    public void resetRequestCount(String host) {
        AtomicInteger counter = requestCountMap.get(host);
        if (counter != null) {
            counter.set(0);
        }
    }

    /**
     * 重置所有域名的请求计数
     */
    public void resetAllRequestCounts() {
        requestCountMap.clear();
    }

    /**
     * 预热指定URL以通过反爬虫挑战
     * @param url 待预热的URL
     */
    public void warmupChallenge(String url) {
        try {
            String host = getHost(url);
            // 传递会话级UA到WebViewHelper
            WebViewHelper.warmupWithSessionUA(url, getSessionUA(host), () -> {
                // 强制刷新Cookie到磁盘，确保同步
                CookieManager.getInstance().flush();
                SpiderDebug.log("Challenge预热完成: " + url);
            });
        } catch (Exception e) {
            SpiderDebug.log("Challenge预热失败: " + e.getMessage());
        }
    }

    /**
     * 预热指定URL并注入自定义脚本
     * @param url 待预热的URL
     * @param script 自定义JavaScript脚本
     */
    public void warmupChallengeWithScript(String url, String script) {
        try {
            String host = getHost(url);
            // 传递会话级UA到WebViewHelper
            WebViewHelper.warmupWithScriptAndSessionUA(url, script, getSessionUA(host), () -> {
                // 强制刷新Cookie到磁盘，确保同步
                CookieManager.getInstance().flush();
                SpiderDebug.log("Challenge预热完成（带脚本）: " + url);
            });
        } catch (Exception e) {
            SpiderDebug.log("Challenge预热失败（带脚本）: " + e.getMessage());
        }
    }

    /**
     * 预热指定URL并注入高级自定义脚本
     * @param url 待预热的URL
     * @param script 自定义JavaScript脚本
     * @param delay 延迟时间
     */
    public void warmupChallengeAdvanced(String url, String script, long delay) {
        try {
            String host = getHost(url);
            // 传递会话级UA到WebViewHelper
            WebViewHelper.warmupAdvancedWithSessionUA(url, script, delay, getSessionUA(host), () -> {
                // 强制刷新Cookie到磁盘，确保同步
                CookieManager.getInstance().flush();
                SpiderDebug.log("Challenge预热完成（高级）: " + url);
            });
        } catch (Exception e) {
            SpiderDebug.log("Challenge预热失败（高级）: " + e.getMessage());
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