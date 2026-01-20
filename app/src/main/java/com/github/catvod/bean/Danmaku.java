package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

/**
 * 弹幕实体类 - 最终优化版
 * [集成示例]:
 * 1. 自动搜索: DanmakuUtil.appendBili(title, duration, list);
 * 2. 快捷添加: list.add(Danmaku.dandan("12345"));
 */
public class Danmaku {
    @SerializedName("name") private String name;
    @SerializedName("url") private String url;
    @SerializedName("source") private String source;
    @SerializedName("type") private String type = "xml";
    @SerializedName("enabled") private Boolean enabled = true;
    @SerializedName("delay") private Integer delay = 0;
    @SerializedName("priority") private Integer priority = 0;
    @SerializedName("headers") private Map<String, String> headers;

    public static Danmaku create() { return new Danmaku(); }

    // --- 预设源工厂 (内置Header防止403) ---

    public static Danmaku bilibili(String oid) {
        return new Danmaku().name("Bilibili").source("bilibili").priority(10)
                .url("https://api.bilibili.com/x/v1/dm/list.so?oid=" + oid)
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0");
    }

    public static Danmaku dandan(String danId) {
        return new Danmaku().name("弹弹Play").source("dandanplay").priority(8)
                .url("https://api.dandanplay.net/api/v2/comment/" + danId + "?withRelated=true")
                .type("json")
                .header("Referer", "https://www.dandanplay.com/")
                .header("User-Agent", "Mozilla/5.0");
    }

    public static Danmaku tucao(String aid) {
        return new Danmaku().name("Tucao").source("tucao").priority(7)
                .url("https://api.tucao.one/api.php?ac=dm&aid=" + aid)
                .type("json");
    }

    // --- 链式配置 ---
    public Danmaku name(String n) { this.name = n; return this; }
    public Danmaku url(String u) { this.url = u; return this; }
    public Danmaku source(String s) { this.source = s; return this; }
    public Danmaku priority(int p) { this.priority = p; return this; }
    public Danmaku type(String t) { this.type = t; return this; }
    
    public Danmaku header(String k, String v) {
        if (headers == null) headers = new HashMap<>();
        headers.put(k, v);
        return this;
    }

    public boolean isValid() { return url != null && url.startsWith("http") && (enabled == null || enabled); }
    public String getUrl() { return url; }
    public int getPriority() { return priority == null ? 0 : priority; }

    @Override
    public Danmaku clone() {
        Danmaku copy = new Danmaku();
        copy.name = name; copy.url = url; copy.source = source;
        copy.type = type; copy.enabled = enabled; copy.delay = delay;
        copy.priority = priority;
        if (headers != null) copy.headers = new HashMap<>(headers);
        return copy;
    }
}
