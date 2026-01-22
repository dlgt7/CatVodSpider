package com.github.catvod.net;

import android.annotation.SuppressLint;
import com.github.catvod.crawler.Spider;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    public static final String POST = "POST";
    public static final String GET = "GET";

    private OkHttpClient client;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    private static OkHttp get() {
        return Loader.INSTANCE;
    }

    // --- 核心方法 ---

    private static OkHttpClient client() {
        if (get().client != null) return get().client;
        try {
            // 优先尝试从 Spider 框架获取已经配置好的 client
            OkHttpClient spiderClient = Spider.client();
            if (spiderClient != null) {
                get().client = spiderClient;
                return spiderClient;
            }
        } catch (Throwable ignored) {}
        return build();
    }

    private static synchronized OkHttpClient build() {
        if (get().client == null) {
            get().client = getBuilder().build();
        }
        return get().client;
    }

    private static OkHttpClient.Builder getBuilder() {
        return new OkHttpClient.Builder()
                .dns(safeDns())
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .followRedirects(true)      // 默认支持重定向
                .followSslRedirects(true)
                .retryOnConnectionFailure(true) // 失败重试
                .hostnameVerifier((hostname, session) -> true)
                .sslSocketFactory(getSSLContext().getSocketFactory(), trustAllCertificates());
    }

    // --- 静态调用接口 ---

    public static String string(String url) {
        return string(url, null, null);
    }

    public static String string(String url, Map<String, String> header) {
        return string(url, null, header);
    }

    public static String string(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(GET, url, params, header).execute(client()).getBody();
    }

    public static OkResult post(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(POST, url, params, header).execute(client());
    }

    public static OkResult post(String url, String json, Map<String, String> header) {
        return new OkRequest(POST, url, json, header).execute(client());
    }

    // --- 工具方法 ---

    public static String getLocation(String url, Map<String, String> header) throws IOException {
        // 临时禁用自动重定向以获取 Location 字段
        OkHttpClient noRedirectClient = client().newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        try (Response response = noRedirectClient.newCall(new Request.Builder().url(url).headers(Headers.of(header)).build()).execute()) {
            return getLocation(response.headers().toMultimap());
        }
    }

    public static String getLocation(Map<String, List<String>> headers) {
        if (headers == null) return null;
        if (headers.containsKey("location")) return headers.get("location").get(0);
        if (headers.containsKey("Location")) return headers.get("Location").get(0);
        return null;
    }

    public static void cancel(String tag) {
        if (tag == null) return;
        for (Call call : client().dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : client().dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    // --- 网络安全配置 ---

    private static Dns safeDns() {
        try {
            Dns spiderDns = Spider.safeDns();
            return spiderDns != null ? spiderDns : Dns.SYSTEM;
        } catch (Throwable e) {
            return Dns.SYSTEM;
        }
    }

    private static SSLContext getSSLContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAllCertificates()}, new SecureRandom());
            return context;
        } catch (Throwable e) {
            return null;
        }
    }

    @SuppressLint({"TrustAllX509TrustManager", "CustomX509TrustManager"})
    private static X509TrustManager trustAllCertificates() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }
}
