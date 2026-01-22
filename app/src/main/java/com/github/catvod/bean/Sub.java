package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

public class Sub {

    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    @SerializedName("ext")
    private String ext;

    @SerializedName("lang")
    private String lang;

    @SerializedName("format")
    private String format;

    @SerializedName("flag")
    private Integer flag; // 2 代表 forced (强制字幕)

    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("vtt", "text/vtt");
        MIME_TYPES.put("ass", "text/x-ssa");
        MIME_TYPES.put("ssa", "text/x-ssa");
        MIME_TYPES.put("srt", "application/x-subrip");
    }

    public static Sub create() {
        return new Sub();
    }

    public Sub name(String name) {
        this.name = name;
        return this;
    }

    public Sub url(String url) {
        this.url = url;
        return this;
    }

    public Sub ext(String ext) {
        this.ext = ext.toLowerCase();
        // 自动根据后缀名设置播放器识别的 MIME Type
        this.format = MIME_TYPES.getOrDefault(this.ext, "application/x-subrip");
        return this;
    }

    public Sub lang(String lang) {
        this.lang = lang;
        return this;
    }

    public Sub forced() {
        this.flag = 2;
        return this;
    }

    // --- Getter 方法，方便播放器调用 ---
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getExt() { return ext; }
    public String getLang() { return lang; }
    public String getFormat() { return format; }
    public Integer getFlag() { return flag; }
}
