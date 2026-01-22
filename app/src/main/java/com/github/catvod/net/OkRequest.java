package com.github.catvod.net;

import android.text.TextUtils;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;
import java.io.IOException;
import java.util.Map;
import okhttp3.FormBody;
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
    private Request request;
    private String url;

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
        this.buildRequest();
    }

    private void buildRequest() {
        Request.Builder builder = new Request.Builder();
        if (method.equals(OkHttp.GET) && params != null) setParams();
        if (method.equals(OkHttp.POST)) builder.post(getRequestBody());
        
        // 注入默认 User-Agent 以提升爬虫兼容性
        builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        if (header != null) {
            for (String key : header.keySet()) builder.addHeader(key, header.get(key));
        }
        request = builder.url(url).build();
    }

    private RequestBody getRequestBody() {
        if (!TextUtils.isEmpty(json)) return RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
        FormBody.Builder formBody = new FormBody.Builder();
        if (params != null) {
            for (String key : params.keySet()) formBody.add(key, params.get(key));
        }
        return formBody.build();
    }

    private void setParams() {
        if (params == null || params.isEmpty()) return;
        StringBuilder sb = new StringBuilder(url);
        // 修复逻辑：判断原 URL 是否已有参数
        if (!url.contains("?")) {
            sb.append("?");
        } else if (!url.endsWith("?") && !url.endsWith("&")) {
            sb.append("&");
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        url = sb.substring(0, sb.length() - 1);
    }

    public OkResult execute(OkHttpClient client) {
        try (Response res = client.newCall(request).execute()) {
            // 使用 try-with-resources 确保 Response 正确关闭
            return new OkResult(res.code(), res.body().string(), res.headers().toMultimap());
        } catch (IOException e) {
            SpiderDebug.log(e);
            return new OkResult();
        }
    }
}
