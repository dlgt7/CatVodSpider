package com.github.catvod.net;

import android.text.TextUtils;
import com.github.catvod.crawler.SpiderDebug;
import java.io.IOException;
import java.net.URLEncoder;
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
    }

    private Request buildRequest() {
        if (method.equals(OkHttp.GET)) {
            prepareGetUrl();
        }

        Request.Builder builder = new Request.Builder().url(url);

        // 1. 注入默认 User-Agent
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // 2. 注入自定义 Header，如果是 UA 则会覆盖默认值
        if (header != null) {
            for (Map.Entry<String, String> entry : header.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        if (method.equals(OkHttp.POST)) {
            builder.post(getRequestBody());
        }

        return builder.build();
    }

    private RequestBody getRequestBody() {
        if (!TextUtils.isEmpty(json)) {
            return RequestBody.create(MediaType.get("application/json; charset=utf-8"), json);
        }
        FormBody.Builder formBody = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                formBody.add(entry.getKey(), entry.getValue());
            }
        }
        return formBody.build();
    }

    /**
     * 核心修复：对 URL 参数进行 URLEncode 编码
     */
    private void prepareGetUrl() {
        if (params == null || params.isEmpty()) return;
        StringBuilder sb = new StringBuilder(url);
        if (!url.contains("?")) {
            sb.append("?");
        } else if (!url.endsWith("?") && !url.endsWith("&")) {
            sb.append("&");
        }

        for (Map.Entry<String, String> entry : params.entrySet()) {
            try {
                // 对 Key 和 Value 进行 UTF-8 编码，防止中文或特殊字符导致请求失败
                sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                  .append("=")
                  .append(URLEncoder.encode(entry.getValue(), "UTF-8"))
                  .append("&");
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        url = sb.substring(0, sb.length() - 1);
    }

    public OkResult execute(OkHttpClient client) {
        try (Response res = client.newCall(buildRequest()).execute()) {
            return new OkResult(res.code(), res.body().string(), res.headers().toMultimap());
        } catch (Throwable e) {
            SpiderDebug.log(e); // 捕获所有异常并记录日志
            return new OkResult();
        }
    }
}
