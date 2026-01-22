package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Objects;

/**
 * CatVod 视频实体类 (原生兼容版)
 * 移除 Lombok 依赖，确保 Gradle 编译通过
 */
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
    private String vodState;          // 状态
    @SerializedName("vod_actor")
    private String vodActor;
    @SerializedName("vod_director")
    private String vodDirector;
    @SerializedName("vod_content")
    private String vodContent;
    @SerializedName("vod_play_from")
    private String vodPlayFrom;       // 播放源
    @SerializedName("vod_play_url")
    private String vodPlayUrl;        // 播放地址
    @SerializedName("vod_play_note")
    private String vodPlayNote;       // 播放备注
    @SerializedName("vod_sub")
    private String vodSub;            // 副标题
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

    // --- 静态解析方法 ---

    public static Vod objectFrom(String str) {
        try {
            if (str == null || str.trim().isEmpty()) return new Vod();
            Vod vod = GSON.fromJson(str, Vod.class);
            return vod == null ? new Vod() : vod;
        } catch (Exception e) {
            return new Vod();
        }
    }

    public static Vod action(String action) {
        Vod vod = new Vod();
        vod.setAction(action);
        return vod;
    }

    // --- 构造函数 ---

    public Vod() {
    }

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

    // --- 业务方法 ---

    public boolean isFolder() {
        return "folder".equals(vodTag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vod vod = (Vod) o;
        return Objects.equals(vodId, vod.vodId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vodId);
    }

    // --- Getter & Setter ---

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getVodId() { return vodId; }
    public void setVodId(String vodId) { this.vodId = vodId; }

    public String getVodName() { return vodName; }
    public void setVodName(String vodName) { this.vodName = vodName; }

    public String getVodPic() { return vodPic; }
    public void setVodPic(String vodPic) { this.vodPic = vodPic; }

    public String getVodRemarks() { return vodRemarks; }
    public void setVodRemarks(String vodRemarks) { this.vodRemarks = vodRemarks; }

    public String getVodYear() { return vodYear; }
    public void setVodYear(String vodYear) { this.vodYear = vodYear; }

    public String getVodArea() { return vodArea; }
    public void setVodArea(String vodArea) { this.vodArea = vodArea; }

    public String getVodLang() { return vodLang; }
    public void setVodLang(String vodLang) { this.vodLang = vodLang; }

    public String getVodState() { return vodState; }
    public void setVodState(String vodState) { this.vodState = vodState; }

    public String getVodActor() { return vodActor; }
    public void setVodActor(String vodActor) { this.vodActor = vodActor; }

    public String getVodDirector() { return vodDirector; }
    public void setVodDirector(String vodDirector) { this.vodDirector = vodDirector; }

    public String getVodContent() { return vodContent; }
    public void setVodContent(String vodContent) { this.vodContent = vodContent; }

    public String getVodPlayFrom() { return vodPlayFrom; }
    public void setVodPlayFrom(String vodPlayFrom) { this.vodPlayFrom = vodPlayFrom; }

    public String getVodPlayUrl() { return vodPlayUrl; }
    public void setVodPlayUrl(String vodPlayUrl) { this.vodPlayUrl = vodPlayUrl; }

    public String getVodPlayNote() { return vodPlayNote; }
    public void setVodPlayNote(String vodPlayNote) { this.vodPlayNote = vodPlayNote; }

    public String getVodSub() { return vodSub; }
    public void setVodSub(String vodSub) { this.vodSub = vodSub; }

    public String getVodScore() { return vodScore; }
    public void setVodScore(String vodScore) { this.vodScore = vodScore; }

    public String getVodDuration() { return vodDuration; }
    public void setVodDuration(String vodDuration) { this.vodDuration = vodDuration; }

    public String getVodTag() { return vodTag; }
    public void setVodTag(String vodTag) { this.vodTag = vodTag; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Style getStyle() { return style; }
    public void setStyle(Style style) { this.style = style; }

    // --- 内部 Style 类 ---

    public static class Style implements Serializable {
        @SerializedName("type")
        private String type;
        @SerializedName("ratio")
        private Float ratio;

        public Style() {}

        public Style(String type, Float ratio) {
            this.type = type;
            this.ratio = ratio;
        }

        public static Style rect() { return rect(0.75f); }
        public static Style rect(float ratio) { return new Style("rect", ratio); }
        public static Style oval() { return new Style("oval", 1.0f); }
        public static Style full() { return new Style("full", null); }
        public static Style list() { return new Style("list", null); }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Float getRatio() { return ratio; }
        public void setRatio(Float ratio) { this.ratio = ratio; }
    }
}
