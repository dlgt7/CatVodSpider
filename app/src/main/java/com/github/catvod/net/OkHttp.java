package com.github.catvod.net;

import android.annotation.SuppressLint;

import com.github.catvod.crawler.Spider;

import java.io.File;
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
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    public static final String POST = "POST";
    public static final String GET = "GET";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String PATCH = "PATCH";

    private OkHttpClient client;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    private static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static Response newCall(String url, String tag) throws IOException {
        return client().newCall(new Request.Builder().url(url).tag(tag).build()).execute();
    }

    public static String string(String url) {
        return string(url, null);
    }

    public static String string(String url, long timeout) {
        return string(url, null, null, timeout);
    }

    public static String string(String url, Map<String, String> header) {
        return string(url, null, header);
    }

    public static String string(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(GET, url, params, header).execute(client()).getBody();
    }

    public static String string(String url, Map<String, String> params, Map<String, String> header, long timeout) {
        return new OkRequest(GET, url, params, header).execute(client(timeout)).getBody();
    }

    public static String post(String url, Map<String, String> params) {
        return post(url, params, null).getBody();
    }

    public static OkResult post(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(POST, url, params, header).execute(client());
    }

    public static String post(String url, String json) {
        return post(url, json, null).getBody();
    }

    public static OkResult post(String url, String json, Map<String, String> header) {
        return new OkRequest(POST, url, json, header).execute(client());
    }

    public static String put(String url, Map<String, String> params) {
        return put(url, params, null).getBody();
    }

    public static OkResult put(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(PUT, url, params, header).execute(client());
    }

    public static String put(String url, String json) {
        return put(url, json, null).getBody();
    }

    public static OkResult put(String url, String json, Map<String, String> header) {
        return new OkRequest(PUT, url, json, header).execute(client());
    }

    public static String delete(String url, Map<String, String> params) {
        return delete(url, params, null).getBody();
    }

    public static OkResult delete(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(DELETE, url, params, header).execute(client());
    }

    public static String delete(String url, String json) {
        return delete(url, json, null).getBody();
    }

    public static OkResult delete(String url, String json, Map<String, String> header) {
        return new OkRequest(DELETE, url, json, header).execute(client());
    }

    public static String patch(String url, Map<String, String> params) {
        return patch(url, params, null).getBody();
    }

    public static OkResult patch(String url, Map<String, String> params, Map<String, String> header) {
        return new OkRequest(PATCH, url, params, header).execute(client());
    }

    public static String patch(String url, String json) {
        return patch(url, json, null).getBody();
    }

    public static OkResult patch(String url, String json, Map<String, String> header) {
        return new OkRequest(PATCH, url, json, header).execute(client());
    }

    public static String upload(String url, Map<String, String> params, Map<String, File> files) {
        return upload(url, params, files, null).getBody();
    }

    public static OkResult upload(String url, Map<String, String> params, Map<String, File> files, Map<String, String> header) {
        return new OkUpload(url, params, files, header).execute(client());
    }

    public static String download(String url, String path) throws IOException {
        return download(url, path, null);
    }

    public static String download(String url, String path, Map<String, String> header) throws IOException {
        Response response = client().newCall(new Request.Builder().url(url).headers(header != null ? Headers.of(header) : new Headers.Builder().build()).build()).execute();
        if (response.isSuccessful() && response.body() != null) {
            File file = new File(path);
            file.getParentFile().mkdirs();
            response.body().byteStream().transferTo(new java.io.FileOutputStream(file));
            return file.getAbsolutePath();
        }
        return "";
    }

    public static byte[] bytes(String url) {
        return bytes(url, null);
    }

    public static byte[] bytes(String url, Map<String, String> header) {
        try {
            Response response = client().newCall(new Request.Builder().url(url).headers(header != null ? Headers.of(header) : new Headers.Builder().build()).build()).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static String getLocation(String url, Map<String, String> header) throws IOException {
        return getLocation(client().newBuilder().followRedirects(false).followSslRedirects(false).build().newCall(new Request.Builder().url(url).headers(Headers.of(header)).build()).execute().headers().toMultimap());
    }

    public static String getLocation(Map<String, List<String>> headers) {
        if (headers == null) return null;
        if (headers.containsKey("location")) return headers.get("location").get(0);
        if (headers.containsKey("Location")) return headers.get("Location").get(0);
        return null;
    }

    public static void cancel(String tag) {
        cancel(client(), tag);
    }

    public static void cancel(OkHttpClient client, String tag) {
        for (Call call : client.dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : client.dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    public static void cancelAll() {
        cancelAll(client());
    }

    public static void cancelAll(OkHttpClient client) {
        client.dispatcher().cancelAll();
    }

    private static OkHttpClient build() {
        if (get().client != null) return get().client;
        return get().client = getBuilder().build();
    }

    private static OkHttpClient.Builder getBuilder() {
        return new OkHttpClient.Builder().dns(safeDns()).connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS).readTimeout(TIMEOUT, TimeUnit.MILLISECONDS).writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS).hostnameVerifier((hostname, session) -> true).sslSocketFactory(getSSLContext().getSocketFactory(), trustAllCertificates()).retryOnConnectionFailure(true).followRedirects(true).followSslRedirects(true);
    }

    private static OkHttpClient client(long timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    private static OkHttpClient client() {
        try {
            return Objects.requireNonNull(Spider.client());
        } catch (Throwable e) {
            return build();
        }
    }

    private static Dns safeDns() {
        try {
            return Objects.requireNonNull(Spider.safeDns());
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
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
