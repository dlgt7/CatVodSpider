package com.github.catvod.net;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.AntiCrawlerEnhancer;
import com.github.catvod.utils.UA;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class OkRequest {

    private final Map<String, String> header;
    private final Map<String, String> params;
    private final String method;
    private final String json;
    private final String userAgent; // 新增User-Agent支持
    private final boolean followRedirects; // 新增重定向控制
    private final int maxRetries; // 新增重试次数
    private final long retryDelayMs; // 新增重试延迟
    private Request request;
    private String url;
    
    // 响应体大小限制（默认10MB）
    private static final long MAX_RESPONSE_SIZE = 10 * 1024 * 1024L;

    // 静态线程池，使用动态配置以适应不同场景
    private static final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "OkRequest-thread");
            t.setDaemon(false); // 设置为非守护线程
            return t;
        }
    });

    OkRequest(String method, String url, Map<String, String> header) {
        this(method, url, (Map<String, String>)null, null, header);
    }

    OkRequest(String method, String url, Map<String, String> params, Map<String, String> header) {
        this(method, url, params, null, header);
    }

    OkRequest(String method, String url, String json, Map<String, String> header) {
        this(method, url, null, json, header);
    }

    private OkRequest(String method, String url, Map<String, String> params, String json, Map<String, String> header) {
        this.url = url;
        this.json = json;
        this.method = method;
        this.params = params;
        this.header = header;
        // 如果没有指定UA，则从AntiCrawlerEnhancer获取会话级UA
        String host = getHostFromUrl(url);
        this.userAgent = (host != null && !host.isEmpty()) ? 
            AntiCrawlerEnhancer.get().getSessionUA(host) : null;
        this.followRedirects = true;
        this.maxRetries = 0;
        this.retryDelayMs = 1000;
        this.buildRequest();
    }

    // 新增构造函数，支持更多配置选项
    public OkRequest(String method, String url, Map<String, String> params, Map<String, String> header, String userAgent, boolean followRedirects) {
        this.url = url;
        this.json = null;
        this.method = method;
        this.params = params;
        this.header = header;
        this.userAgent = userAgent;
        this.followRedirects = followRedirects;
        this.maxRetries = 0;
        this.retryDelayMs = 1000;
        this.buildRequest();
    }

    public OkRequest(String method, String url, String json, Map<String, String> header, String userAgent, boolean followRedirects) {
        this.url = url;
        this.json = json;
        this.method = method;
        this.params = null;
        this.header = header;
        this.userAgent = userAgent;
        this.followRedirects = followRedirects;
        this.maxRetries = 0;
        this.retryDelayMs = 1000;
        this.buildRequest();
    }

    // 新增构造函数，支持重试机制
    public OkRequest(String method, String url, Map<String, String> params, Map<String, String> header, String userAgent, 
                     boolean followRedirects, int maxRetries, long retryDelayMs) {
        this.url = url;
        this.json = null;
        this.method = method;
        this.params = params;
        this.header = header;
        this.userAgent = userAgent;
        this.followRedirects = followRedirects;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.buildRequest();
    }

    public OkRequest(String method, String url, String json, Map<String, String> header, String userAgent, 
                     boolean followRedirects, int maxRetries, long retryDelayMs) {
        this.url = url;
        this.json = json;
        this.method = method;
        this.params = null;
        this.header = header;
        this.userAgent = userAgent;
        this.followRedirects = followRedirects;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.buildRequest();
    }

    private void buildRequest() {
        Request.Builder builder = new Request.Builder();
        
        // 根据不同的HTTP方法设置请求体
        if (method.equals(OkHttp.GET) && params != null) {
            setParams();
        } else if (method.equals(OkHttp.POST)) {
            builder.post(getRequestBody());
        } else if (method.equalsIgnoreCase("PUT")) {
            builder.put(getRequestBody());
        } else if (method.equalsIgnoreCase("DELETE")) {
            if (params != null && !params.isEmpty()) {
                // DELETE 请求带参数时使用请求体
                builder.delete(getRequestBody());
            } else {
                builder.delete();
            }
        } else if (method.equalsIgnoreCase("PATCH")) {
            builder.patch(getRequestBody());
        }
        
        // 添加请求头
        if (header != null) {
            for (String key : header.keySet()) {
                builder.addHeader(key, header.get(key));
            }
        }
        
        // 设置User-Agent，优先使用指定的UA，否则使用会话级UA，最后使用随机UA
        if (!TextUtils.isEmpty(userAgent)) {
            builder.addHeader("User-Agent", userAgent);
        } else {
            // 从URL中提取主机名，获取会话级UA
            String host = getHostFromUrl(url);
            if (host != null && !host.isEmpty()) {
                builder.addHeader("User-Agent", AntiCrawlerEnhancer.get().getSessionUA(host));
            } else {
                // 默认使用随机User-Agent
                builder.addHeader("User-Agent", UA.getRandom());
            }
        }
        
        request = builder.url(url).build();
    }

    private RequestBody getRequestBody() {
        if (!TextUtils.isEmpty(json)) return RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        FormBody.Builder formBody = new FormBody.Builder();
        if (params != null) for (String key : params.keySet()) formBody.add(key, params.get(key));
        return formBody.build();
    }

    private void setParams() {
        // 使用OkHttp的HttpUrl.Builder来安全地构建URL，处理编码和参数拼接
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        url = urlBuilder.build().toString();
    }

    /**
     * 从URL中提取主机名
     * @param url URL字符串
     * @return 主机名
     */
    private String getHostFromUrl(String url) {
        try {
            java.net.URL netUrl = new java.net.URL(url);
            return netUrl.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    public OkResult execute(OkHttpClient client) {
        return executeWithRetry(client, 0);
    }

    private OkResult executeWithRetry(OkHttpClient client, int attempt) {
        try (Response res = client.newBuilder()
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .build()
                .newCall(request).execute()) {
            
            // 添加对响应体的空值检查和大小限制，防止 OOM
            String bodyContent = "";
            if (res.body() != null) {
                // 检查Content-Length头部，如果存在且超过限制，则不读取body
                String contentLengthHeader = res.header("Content-Length");
                if (contentLengthHeader != null) {
                    try {
                        long contentLength = Long.parseLong(contentLengthHeader);
                        if (contentLength > MAX_RESPONSE_SIZE) {
                            SpiderDebug.log(new Exception("Response size too large: " + contentLength + " bytes, max allowed: " + MAX_RESPONSE_SIZE + ", URL: " + request.url().toString()));
                            // 返回一个特殊的错误结果而不是尝试读取大数据
                            return new OkResult(res.code(), "", res.headers().toMultimap());
                        }
                    } catch (NumberFormatException e) {
                        // 如果无法解析Content-Length，则继续尝试读取，但要小心
                    }
                }
                
                // 对于分块传输编码的响应，我们仍然需要读取整个响应体
                // 但在此之前，我们可以先读取一部分来估计大小
                bodyContent = res.body().string();
                
                // 再次检查字符串长度（以防Content-Length头部不存在）
                if (bodyContent.length() > MAX_RESPONSE_SIZE) {
                    SpiderDebug.log(new Exception("Response body too large: " + bodyContent.length() + " chars, max allowed: " + MAX_RESPONSE_SIZE + ", URL: " + request.url().toString()));
                    return new OkResult(res.code(), "", res.headers().toMultimap());
                }
            }
            
            return new OkResult(res.code(), bodyContent, res.headers().toMultimap());
        } catch (IOException e) {
            if (attempt < maxRetries) {
                try {
                    // 实现真正的指数退避算法
                    long currentDelay = (long) (retryDelayMs * Math.pow(2, attempt));
                    // 添加随机抖动（±25%）
                    double jitter = 0.75 + Math.random() * 0.5; // 0.75~1.25
                    long delayWithJitter = Math.round(currentDelay * jitter);
                    Thread.sleep(delayWithJitter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // 恢复中断状态
                    return new OkResult();
                }
                return executeWithRetry(client, attempt + 1);
            }
            SpiderDebug.log(e);
            return new OkResult();
        } catch (OutOfMemoryError e) {
            // 捕获可能的内存溢出错误
            SpiderDebug.log(new Exception("Out of memory error when reading response body: " + e.getMessage()));
            return new OkResult(-1, "", null);
        }
    }

    /**
     * 执行请求并返回原始Response对象，不读取body
     * @param client OkHttpClient实例
     * @return Response对象
     */
    public Response executeRaw(OkHttpClient client) {
        try {
            return client.newBuilder()
                    .followRedirects(followRedirects)
                    .followSslRedirects(followRedirects)
                    .build()
                    .newCall(request).execute();
        } catch (IOException e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    /**
     * 执行请求，允许自定义客户端配置
     */
    public OkResult executeWithCustomClient(OkHttpClient customClient) {
        return executeWithRetry(customClient, 0);
    }

    /**
     * 异步执行请求
     */
    public CompletableFuture<OkResult> executeAsync(OkHttpClient client) {
        CompletableFuture<OkResult> future = new CompletableFuture<>();
        
        executor.execute(() -> {
            try {
                OkResult result = execute(client);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }

    /**
     * 异步执行请求，使用自定义客户端
     */
    public CompletableFuture<OkResult> executeAsyncWithCustomClient(OkHttpClient customClient) {
        CompletableFuture<OkResult> future = new CompletableFuture<>();
        
        executor.execute(() -> {
            try {
                OkResult result = executeWithCustomClient(customClient);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
}