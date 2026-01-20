package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

/**
 * 强化版弹幕实体类
 * [实战用法参考]：
 * 1. 自动匹配B站: DanmakuUtil.appendBili(title, duration, list);
 * 2. 多源合并: Result.get().danmaku(DanmakuUtil.merge(list1, list2));
 * * [内容去重建议]:
 * 不建议在此层级下载XML比对。请在播放器端拉取解析后，使用 Map<Long, String> (时间戳->内容MD5) 过滤。
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

    public Danmaku() {}

    public static Danmaku create() {
        return new Danmaku();
    }

    // --- 预设工厂方法 (内置Header防止403) ---

    public static Danmaku bilibili(String oid) {
        return new Danmaku()
                .name("Bilibili")
                .source("bilibili")
                .url("https://api.bilibili.com/x/v1/dm/list.so?oid=" + oid)
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0")
                .priority(10);
    }

    // --- 链式配置 ---

    public Danmaku name(String name) { this.name = name; return this; }
    public Danmaku url(String url) { this.url = url; return this; }
    public Danmaku source(String source) { this.source = source; return this; }
    public Danmaku type(String type) { this.type = type; return this; }
    public Danmaku priority(int priority) { this.priority = priority; return this; }
    public Danmaku header(String k, String v) {
        if (headers == null) headers = new HashMap<>();
        headers.put(k, v);
        return this;
    }

    public boolean isValid() {
        return url != null && url.startsWith("http") && (enabled == null || enabled);
    }

    public String getUrl() { return url; }
    public int getPriority() { return priority == null ? 0 : priority; }

    @Override
    public Danmaku clone() {
        Danmaku copy = new Danmaku();
        copy.name = name;
        copy.url = url;
        copy.source = source;
        copy.type = type;
        copy.enabled = enabled;
        copy.delay = delay;
        copy.priority = priority;
        if (headers != null) copy.headers = new HashMap<>(headers);
        return copy;
    }
}
