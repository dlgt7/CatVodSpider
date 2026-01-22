package com.github.catvod.net;

import android.text.TextUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OkResult {

    private final int code;
    private final String body;
    private final Map<String, List<String>> resp;

    public OkResult() {
        this.code = 500;
        this.body = "";
        this.resp = new HashMap<>();
    }

    public OkResult(int code, String body, Map<String, List<String>> resp) {
        this.code = code;
        this.body = body;
        this.resp = resp;
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return TextUtils.isEmpty(body) ? "" : body;
    }

    public Map<String, List<String>> getResp() {
        return resp;
    }

    // --- 新增功能 ---

    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    public String getHeader(String name) {
        if (resp == null) return "";
        List<String> values = resp.get(name);
        if (values == null || values.isEmpty()) {
            values = resp.get(name.toLowerCase()); // 兼容大小写
        }
        return (values == null || values.isEmpty()) ? "" : values.get(0);
    }
}
