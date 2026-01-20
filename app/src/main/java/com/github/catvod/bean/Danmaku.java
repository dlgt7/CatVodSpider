package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹幕实体类 - 支持多源集成与智能配置
 * * [实战举例]：
 * 1. 基础用法：
 * Danmaku dm = Danmaku.create().name("官方").url("http://x.com/dm.xml").priority(20);
 * * 2. 多源集成 (在 Spider 的 playerContent 中使用):
 * List<Danmaku> dms = new ArrayList<>();
 * dms.add(Danmaku.create().name("网站原生").url("http://site.com/api").priority(20)); // 高优先级
 * dms.add(Danmaku.bilibili("1234567")); // 自动带上B站Header, 优先级10
 * * // 最后返回结果
 * return Result.get().url(playUrl).danmaku(DanmakuUtil.merge(dms)).string();
 */
public class Danmaku {

    @SerializedName("name")
    private String name;           // 弹幕源名称 (如: B站、A站、官方)
    @SerializedName("url")
    private String url;            // 弹幕API接口地址
    @SerializedName("source")
    private String source;         // 来源标识
    @SerializedName("type")
    private String type = "xml";   // 弹幕格式: xml (默认), json, ass
    @SerializedName("enabled")
    private Boolean enabled = true; 
    @SerializedName("delay")
    private Integer delay = 0;     // 偏移量(毫秒)
    @SerializedName("priority")
    private Integer priority = 0;  // 优先级 (数字越大越靠前)
    @SerializedName("headers")
    private Map<String, String> headers; // 请求头 (用于绕过防盗链)
    @SerializedName("extra")
    private String extra;          // 扩展信息

    public static Danmaku create() {
        return new Danmaku();
    }

    // 快捷创建 B 站弹幕配置
    public static Danmaku bilibili(String oid) {
        return new Danmaku()
                .name("Bilibili")
                .source("bilibili")
                .url("https://api.bilibili.com/x/v1/dm/list.so?oid=" + oid)
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0")
                .priority(10);
    }

    // --- 链式调用方法 ---
    public Danmaku name(String name) { this.name = name; return this; }
    public Danmaku url(String url) { this.url = url; return this; }
    public Danmaku source(String source) { this.source = source; return this; }
    public Danmaku type(String type) { this.type = type; return this; }
    public Danmaku priority(int priority) { this.priority = priority; return this; }
    public Danmaku enabled(boolean enabled) { this.enabled = enabled; return this; }
    
    public Danmaku header(String key, String value) {
        if (this.headers == null) this.headers = new HashMap<>();
        this.headers.put(key, value);
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
        copy.name = this.name;
        copy.url = this.url;
        copy.source = this.source;
        copy.type = this.type;
        copy.enabled = this.enabled;
        copy.delay = this.delay;
        copy.priority = this.priority;
        if (this.headers != null) copy.headers = new HashMap<>(this.headers);
        return copy;
    }
}
