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
        this(500, "", new HashMap<>());
    }

    public OkResult(int code, String body, Map<String, List<String>> resp) {
        this.code = code;
        this.body = body;
        this.resp = resp != null ? resp : new HashMap<>();
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

    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    /**
     * 完全不区分大小写的 Header 获取方式
     */
    public String getHeader(String name) {
        if (name == null) return "";
        for (Map.Entry<String, List<String>> entry : resp.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                return (values == null || values.isEmpty()) ? "" : values.get(0);
            }
        }
        return "";
    }
}
