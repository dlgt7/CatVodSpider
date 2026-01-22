package com.github.catvod.utils;

import okhttp3.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OkHttpUtil {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true) // 失败自动重试
            .build();

    /**
     * 同步执行 HTTP 请求
     * @param url 请求地址
     * @param method GET 或 POST
     * @param headers 请求头 Map
     * @param body POST 提交的字符串（GET 时传 null）
     * @return 响应字符串
     */
    public static String execute(String url, String method, Map<String, String> headers, String body) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);

        // 1. 设置默认 UA，伪装成 Chrome 浏览器
        builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        builder.addHeader("Accept", "*/*");

        // 2. 注入 JS 传入的自定义 Headers
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        // 3. 处理请求动作
        if (method.equalsIgnoreCase("POST")) {
            RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), 
                body == null ? "" : body
            );
            builder.post(requestBody);
        } else {
            builder.get();
        }

        // 4. 发送同步请求
        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            return "";
        }
    }
}
