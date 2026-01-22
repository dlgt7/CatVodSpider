package com.github.catvod.net;

import android.annotation.SuppressLint;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
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

    private static final long DEFAULT_TIMEOUT = 15000; // 15s
    public static final String POST = "POST";
    public static final String GET = "GET";

    private OkHttpClient client;
    private OkHttpClient noRedirectClient;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    public static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static OkHttpClient client() {
        if (get().client == null) {
            synchronized (OkHttp.class) {
                if (get().client == null) {
                    get().client = build(true); // 默认忽略 SSL 以兼容老旧站点
                }
            }
        }
        return get().client;
    }

    private static OkHttpClient build(boolean ignoreSSL) {
        try {
            // 尝试获取 Spider 框架 client
            OkHttpClient spiderClient = Spider.client();
            if (spiderClient != null) return spiderClient;
        } catch (Throwable ignored) {}

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .dns(safeDns())
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true);

        if (ignoreSSL) {
            builder.hostnameVerifier((hostname, session) -> true)
                   .sslSocketFactory(getSSLContext().getSocketFactory(), trustAllCertificates());
        }
        return builder.build();
    }

    private OkHttpClient getNoRedirectClient() {
        if (noRedirectClient == null) {
            noRedirectClient = client().newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();
        }
        return noRedirectClient;
    }

    // --- 静态调用接口 ---

    public static String string(String url, Map<String, String> header) {
        return new OkRequest(GET, url, null, header).execute(client()).getBody();
    }

    public static OkResult post(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(POST, url, params, header).execute(client());
    }

    public static OkResult post(String url, String json, Map<String, String> header) {
        return new OkRequest(POST, url, json, header).execute(client());
    }

    // --- 工具方法 ---

    public static String getLocation(String url, Map<String, String> header) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (header != null) builder.headers(Headers.of(header));
        
        try (Response response = get().getNoRedirectClient().newCall(builder.build()).execute()) {
            return getLocation(response.headers().toMultimap());
        }
    }

    public static String getLocation(Map<String, List<String>> headers) {
        if (headers == null) return null;
        for (String key : headers.keySet()) {
            if ("location".equalsIgnoreCase(key)) return headers.get(key).get(0);
        }
        return null;
    }

    public static void cancel(String tag) {
        if (tag == null) return;
        for (Call call : client().dispatcher().queuedCalls()) {
            if (tag.equals(call.request().tag())) call.cancel();
        }
        for (Call call : client().dispatcher().runningCalls()) {
            if (tag.equals(call.request().tag())) call.cancel();
        }
    }

    private static Dns safeDns() {
        try {
            return Spider.safeDns() != null ? Spider.safeDns() : Dns.SYSTEM;
        } catch (Throwable e) {
            return Dns.SYSTEM;
        }
    }

    private static SSLContext getSSLContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAllCertificates()}, new SecureRandom());
            return context;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    @SuppressLint("CustomX509TrustManager")
    private static X509TrustManager trustAllCertificates() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }
}
