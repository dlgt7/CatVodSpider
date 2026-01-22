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
            .retryOnConnectionFailure(true)
            .build();

    public static String execute(String url, String method, Map<String, String> headers, String body) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);
        
        // 默认 UA
        builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        if (method.equalsIgnoreCase("POST")) {
            RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), 
                body == null ? "" : body
            );
            builder.post(requestBody);
        } else {
            builder.get();
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            return "";
        }
    }
}
