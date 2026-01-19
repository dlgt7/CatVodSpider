package com.github.catvod.spider;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代理管理器
 * 1. 管理本地代理端口
 * 2. 提供代理URL构建功能
 * 3. 支持动态端口发现
 * 4. 增强错误处理和容错能力
 * 5. 支持复杂视频源预热处理
 * 6. 提供视频流代理转发功能
 * 7. 内置LRU缓存机制
 * 8. 线程安全的缓存访问
 * 9. 资源安全释放
 */
public class Proxy {

    private static Method method;
    private static int port;
    private static final int MIN_PORT = 8964;
    private static final int MAX_PORT = 9999;
    private static final String PROXY_CHECK_PATH = "/proxy?do=ck";
    private static final String PROXY_BASE_URL = "http://127.0.0.1:";
    private static final String PROXY_DEFAULT_PATH = "/proxy";
    
    // 内置LRU缓存，用于缓存已解析的URL，使用Collections.synchronizedMap确保线程安全
    private static final int CACHE_SIZE = 100;
    private static final Map<String, String> urlCache = Collections.synchronizedMap(
        new LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > CACHE_SIZE;
            }
        }
    );

    /**
     * 代理请求处理
     * @param params 请求参数
     * @return 代理响应对象数组 [状态码, 内容类型, 响应流]
     */
    public static Object[] proxy(Map<String, String> params) {
        if (params == null) {
            return new Object[]{500, "text/plain; charset=utf-8", 
                new ByteArrayInputStream("Error: params is null".getBytes(StandardCharsets.UTF_8))};
        }
        
        String doParam = params.get("do");
        if ("ck".equals(doParam)) {
            return new Object[]{200, "text/plain; charset=utf-8", 
                new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8))};
        }
        
        // 处理视频流代理请求
        if ("video".equals(doParam)) {
            return handleVideoProxy(params);
        }
        
        // 处理复杂源预热请求
        if ("warmup".equals(doParam)) {
            return handleWarmupRequest(params);
        }
        
        // 返回404表示不支持的请求
        return new Object[]{404, "text/plain; charset=utf-8", 
            new ByteArrayInputStream("Not Found".getBytes(StandardCharsets.UTF_8))};
    }

    /**
     * 处理视频流代理请求
     * @param params 请求参数
     * @return 代理响应对象数组
     */
    private static Object[] handleVideoProxy(Map<String, String> params) {
        String videoUrl = params.get("url");
        if (videoUrl == null || videoUrl.isEmpty()) {
            return new Object[]{400, "text/plain; charset=utf-8", 
                new ByteArrayInputStream("Bad Request: Missing video URL".getBytes(StandardCharsets.UTF_8))};
        }
        
        // 检查缓存 - 使用同步块确保线程安全
        String cachedUrl;
        synchronized (urlCache) {
            cachedUrl = urlCache.get(videoUrl);
        }
        
        if (cachedUrl != null) {
            // 如果缓存命中，直接使用缓存的URL
            videoUrl = cachedUrl;
        }
        
        // 使用反爬虫增强器获取增强的请求头
        Map<String, String> enhancedHeaders = AntiCrawlerEnhancer.get().getEnhancedHeaders(videoUrl, "xhr");
        
        // 如果提供了额外的请求头参数，合并到增强的请求头中
        String extraHeaders = params.get("headers");
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            // 这里可以解析额外的请求头（例如JSON格式）
            // 暂时跳过，可根据实际需求实现
        }
        
        try {
            // 执行请求获取视频流
            okhttp3.Response response = OkHttp.getClient().newCall(
                new okhttp3.Request.Builder()
                    .url(videoUrl)
                    .headers(okhttp3.Headers.of(enhancedHeaders))
                    .build()
            ).execute();
            
            if (!response.isSuccessful()) {
                response.close(); // 确保Response被关闭
                return new Object[]{response.code(), "text/plain; charset=utf-8", 
                    new ByteArrayInputStream(("Error: " + response.code()).getBytes(StandardCharsets.UTF_8))};
            }
            
            // 获取响应体
            okhttp3.ResponseBody body = response.body();
            if (body == null) {
                response.close(); // 确保Response被关闭
                return new Object[]{500, "text/plain; charset=utf-8", 
                    new ByteArrayInputStream("Error: Empty response body".getBytes(StandardCharsets.UTF_8))};
            }
            
            // 获取内容类型
            String contentType = response.header("Content-Type");
            if (contentType == null) {
                contentType = "application/octet-stream"; // 默认二进制流
            }
            
            // 将成功请求的URL放入缓存 - 使用同步块确保线程安全，只在URL确实发生变化时才更新缓存
            String originalUrl = params.get("url");
            if (!originalUrl.equals(videoUrl)) {
                // 只有当URL确实发生变化时才更新缓存
                synchronized (urlCache) {
                    urlCache.put(originalUrl, videoUrl);
                }
            }
            
            // 创建包装的InputStream，确保在流关闭时也关闭Response
            InputStream inputStream = new InputStreamWrapper(body.byteStream(), response);
            return new Object[]{200, contentType, inputStream};
            
        } catch (Exception e) {
            SpiderDebug.log("Video proxy error: " + e.getMessage());
            return new Object[]{500, "text/plain; charset=utf-8", 
                new ByteArrayInputStream(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8))};
        }
    }

    /**
     * 处理复杂源预热请求
     * @param params 请求参数
     * @return 预热结果
     */
    private static Object[] handleWarmupRequest(Map<String, String> params) {
        try {
            String url = params.get("url");
            if (url == null || url.isEmpty()) {
                return new Object[]{400, "text/plain; charset=utf-8", 
                    new ByteArrayInputStream("Bad Request: Missing URL for warmup".getBytes(StandardCharsets.UTF_8))};
            }
            
            // 执行预热挑战
            AntiCrawlerEnhancer.get().warmupChallenge(url);
            
            return new Object[]{200, "text/plain; charset=utf-8", 
                new ByteArrayInputStream("Warmup completed".getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            SpiderDebug.log("Warmup error: " + e.getMessage());
            return new Object[]{500, "text/plain; charset=utf-8", 
                new ByteArrayInputStream(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8))};
        }
    }

    /**
     * 初始化代理配置
     * 尝试反射获取端口，失败则自动发现
     */
    public static void init() {
        try {
            Class<?> clz = Class.forName("com.github.catvod.Proxy");
            port = (int) clz.getMethod("getPort").invoke(null);
            method = clz.getMethod("getUrl", boolean.class);
            SpiderDebug.log("本地代理端口:" + port);
        } catch (Throwable e) {
            SpiderDebug.log("反射获取代理端口失败，尝试自动发现: " + e.getMessage());
            findPort();
        }
    }

    /**
     * 获取当前代理端口
     * @return 代理端口号
     */
    public static int getPort() {
        return port;
    }

    /**
     * 构建代理URL
     * @param siteKey 站点密钥
     * @param param 附加参数
     * @return 代理URL字符串
     */
    public static String getUrl(String siteKey, String param) {
        if (siteKey == null) siteKey = "";
        if (param == null) param = "";
        return "proxy://do=csp&siteKey=" + siteKey + param;
    }

    /**
     * 构建视频代理URL
     * @param videoUrl 视频URL
     * @param extraHeaders 额外请求头（可选）
     * @return 视频代理URL
     */
    public static String getVideoProxyUrl(String videoUrl, String extraHeaders) {
        if (videoUrl == null) videoUrl = "";
        // 使用Uri.encode替代URLEncoder以避免空格转+号的问题
        String encodedUrl = encodeUrl(videoUrl);
        String url = "proxy://do=video&url=" + encodedUrl;
        if (extraHeaders != null) {
            url += "&headers=" + encodeUrl(extraHeaders);
        }
        return url;
    }

    /**
     * 使用适当的URL编码方法
     * @param url 待编码的URL
     * @return 编码后的URL
     */
    private static String encodeUrl(String url) {
        if (url == null) return "";
        // 使用URLEncoder并修复空格问题（+号转%20）
        try {
            String encoded = java.net.URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
            // 将+替换为%20，以确保URL正确性
            return encoded.replace("+", "%20");
        } catch (Exception e) {
            SpiderDebug.log("URL encoding failed: " + e.getMessage());
            return url; // 如果编码失败，返回原URL
        }
    }

    /**
     * 构建预热代理URL
     * @param url 待预热的URL
     * @return 预热代理URL
     */
    public static String getWarmupProxyUrl(String url) {
        if (url == null) url = "";
        String encodedUrl = encodeUrl(url);
        return "proxy://do=warmup&url=" + encodedUrl;
    }

    /**
     * 获取默认代理URL
     * @return 代理URL字符串
     */
    public static String getUrl() {
        return getUrl(true);
    }

    /**
     * 获取代理URL
     * @param local 是否本地代理
     * @return 代理URL字符串
     */
    public static String getUrl(boolean local) {
        try {
            return (String) method.invoke(null, local);
        } catch (Throwable e) {
            SpiderDebug.log("反射获取代理URL失败，使用默认URL: " + e.getMessage());
            return PROXY_BASE_URL + port + PROXY_DEFAULT_PATH;
        }
    }

    /**
     * 自动发现可用代理端口
     * 在MIN_PORT到MAX_PORT范围内查找可用端口
     * 使用短超时时间避免初始化卡顿
     */
    private static void findPort() {
        if (port > 0) return;
        
        SpiderDebug.log("开始自动发现代理端口...");
        for (int p = MIN_PORT; p < MAX_PORT; p++) {
            try {
                String testUrl = PROXY_BASE_URL + p + PROXY_CHECK_PATH;
                
                // 使用短超时时间的OkHttpClient进行端口探测
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)  // 连接超时500ms
                    .readTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)     // 读取超时500ms
                    .writeTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)    // 写入超时500ms
                    .build();
                
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(testUrl)
                    .build();
                
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && "ok".equals(response.body().string())) {
                        SpiderDebug.log("本地代理端口发现成功:" + p);
                        port = p;
                        return;
                    }
                } catch (IOException e) {
                    // 连接失败，继续尝试下一个端口
                    continue;
                }
            } catch (Exception e) {
                // 其他异常，继续尝试下一个端口
                continue;
            }
        }
        
        if (port == 0) {
            SpiderDebug.log("未能找到可用的代理端口，使用默认端口8964");
            port = 8964; // 如果没找到，使用默认端口
        }
    }
    
    /**
     * 检查代理是否可用
     * @return 如果代理可用返回true，否则返回false
     */
    public static boolean isProxyAvailable() {
        if (port <= 0) {
            return false;
        }
        
        try {
            String testUrl = PROXY_BASE_URL + port + PROXY_CHECK_PATH;
            String response = OkHttp.getString(testUrl);
            return "ok".equals(response);
        } catch (Exception e) {
            SpiderDebug.log("代理连接测试失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 重置代理配置
     */
    public static void reset() {
        port = 0;
        method = null;
        // 清空缓存
        synchronized (urlCache) {
            urlCache.clear();
        }
    }
    
    /**
     * 获取缓存大小
     * @return 当前缓存中的条目数量
     */
    public static int getCacheSize() {
        synchronized (urlCache) {
            return urlCache.size();
        }
    }
    
    /**
     * 清空URL缓存
     */
    public static void clearCache() {
        synchronized (urlCache) {
            urlCache.clear();
        }
    }
    
    /**
     * 包装InputStream以确保Response被正确关闭
     */
    private static class InputStreamWrapper extends InputStream {
        private final InputStream wrappedStream;
        private final okhttp3.Response response;
        private volatile boolean closed = false;

        public InputStreamWrapper(InputStream wrappedStream, okhttp3.Response response) {
            this.wrappedStream = wrappedStream;
            this.response = response;
        }

        @Override
        public int read() throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            return wrappedStream.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            return wrappedStream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            return wrappedStream.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            return wrappedStream.skip(n);
        }

        @Override
        public int available() throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            return wrappedStream.available();
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    wrappedStream.close();
                } finally {
                    // 确保Response被关闭，释放资源
                    if (response != null) {
                        response.close();
                    }
                }
            }
        }

        @Override
        public void mark(int readlimit) {
            wrappedStream.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            wrappedStream.reset();
        }

        @Override
        public boolean markSupported() {
            return wrappedStream.markSupported();
        }
    }
}