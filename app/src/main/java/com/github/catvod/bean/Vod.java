package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * CatVod 视频实体类 (2025-2026 增强版)
 * 适配 TVBox, FongMi, CatVodSpider 等主流生态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vod implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new Gson();

    @SerializedName("type_name")
    private String typeName;
    @SerializedName("vod_id")
    private String vodId;
    @SerializedName("vod_name")
    private String vodName;
    @SerializedName("vod_pic")
    private String vodPic;
    @SerializedName("vod_remarks")
    private String vodRemarks;
    @SerializedName("vod_year")
    private String vodYear;
    @SerializedName("vod_area")
    private String vodArea;
    @SerializedName("vod_lang")
    private String vodLang;           // 语言
    @SerializedName("vod_state")
    private String vodState;          // 状态 (如: 连载中)
    @SerializedName("vod_actor")
    private String vodActor;
    @SerializedName("vod_director")
    private String vodDirector;
    @SerializedName("vod_content")
    private String vodContent;
    @SerializedName("vod_play_from")
    private String vodPlayFrom;       // 播放源 (如: qq$$$youku)
    @SerializedName("vod_play_url")
    private String vodPlayUrl;        // 播放地址
    @SerializedName("vod_play_note")
    private String vodPlayNote;       // 播放备注
    @SerializedName("vod_sub")
    private String vodSub;            // 副标题/别名
    @SerializedName("vod_score")
    private String vodScore;          // 评分
    @SerializedName("vod_duration")
    private String vodDuration;       // 时长
    @SerializedName("vod_tag")
    private String vodTag;            // folder / file
    @SerializedName("action")
    private String action;
    @SerializedName("style")
    private Style style;

    // --- 静态解析与快速构建 ---

    public static Vod objectFrom(String str) {
        try {
            if (str == null || str.trim().isEmpty()) return new Vod();
            return Optional.ofNullable(GSON.fromJson(str, Vod.class)).orElseGet(Vod::new);
        } catch (Exception e) {
            return new Vod();
        }
    }

    public static Vod action(String action) {
        return Vod.builder().action(action).build();
    }

    // --- 传统构造器兼容 (手动保留以支持旧代码直接 new) ---

    public Vod(String vodId, String vodName, String vodPic) {
        this.vodId = vodId;
        this.vodName = vodName;
        this.vodPic = vodPic;
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks) {
        this(vodId, vodName, vodPic);
        this.vodRemarks = vodRemarks;
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, boolean folder) {
        this(vodId, vodName, vodPic, vodRemarks);
        this.vodTag = folder ? "folder" : "file";
    }

    // --- 业务逻辑方法 ---

    public boolean isFolder() {
        return "folder".equals(vodTag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vod vod = (Vod) o;
        return Objects.equals(vodId, vod.vodId); // 以 vodId 作为唯一标识
    }

    @Override
    public int hashCode() {
        return Objects.hash(vodId);
    }

    // --- 内部 Style 类 ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Style implements Serializable {
        @SerializedName("type")
        private String type;
        @SerializedName("ratio")
        private Float ratio;

        public static Style rect() { return rect(0.75f); }
        public static Style rect(float ratio) { return new Style("rect", ratio); }
        public static Style oval() { return new Style("oval", 1.0f); }
        public static Style full() { return new Style("full", null); }
        public static Style list() { return new Style("list", null); }
    }
}
