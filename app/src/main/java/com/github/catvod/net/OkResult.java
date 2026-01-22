package com.github.catvod.net;

import android.text.TextUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OkResult {

    private final int code;
    private final String body;
    private final Map<String, List<String>> resp;

    /**
     * 默认错误构造函数
     */
    public OkResult() {
        this(500, "", new HashMap<>());
    }

    public OkResult(int code, String body, Map<String, List<String>> resp) {
        this.code = code;
        this.body = body != null ? body : "";
        // 使用不可变 Map 包装响应头，防止调用者修改
        this.resp = resp != null ? Collections.unmodifiableMap(resp) : new HashMap<>();
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

    /**
     * 判断请求是否在 2xx 范围内
     */
    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    /**
     * 完全不区分大小写的 Header 获取逻辑
     * 解决了服务器返回 LOCATION 或 Location 导致获取不到的问题
     */
    public String getHeader(String name) {
        if (name == null || resp == null) return "";
        for (Map.Entry<String, List<String>> entry : resp.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                return (values == null || values.isEmpty()) ? "" : values.get(0);
            }
        }
        return "";
    }
}
