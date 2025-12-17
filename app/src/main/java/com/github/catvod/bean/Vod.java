package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Vod {

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

    @SerializedName("vod_actor")
    private String vodActor;

    @SerializedName("vod_director")
    private String vodDirector;

    @SerializedName("vod_content")
    private String vodContent;

    @SerializedName("vod_play_from")
    private String vodPlayFrom;

    @SerializedName("vod_play_url")
    private String vodPlayUrl;

    @SerializedName("vod_tag")
    private String vodTag;

    @SerializedName("action")
    private String action;

    @SerializedName("style")
    private Style style;

    // ============================ 静态工厂方法 ============================

    public static Vod objectFrom(String str) {
        Vod item = new Gson().fromJson(str, Vod.class);
        return item == null ? new Vod() : item;
    }

    public static Vod action(String action) {
        Vod vod = new Vod();
        vod.action = action;
        return vod;
    }

    // ============================ 构造器 ============================

    public Vod() {
    }

    public Vod(String vodId, String vodName, String vodPic) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, String action) {
        this(vodId, vodName, vodPic, vodRemarks);
        setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style) {
        this(vodId, vodName, vodPic, vodRemarks);
        setStyle(style);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style, String action) {
        this(vodId, vodName, vodPic, vodRemarks, style);
        setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, boolean folder) {
        this(vodId, vodName, vodPic, vodRemarks);
        setVodTag(folder ? "folder" : "file");
    }

    // ============================ Getter 方法（完整补充） ============================

    public String getTypeName() {
        return typeName;
    }

    public String getVodId() {
        return vodId;
    }

    public String getVodName() {
        return vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public String getVodRemarks() {
        return vodRemarks;
    }

    public String getVodYear() {
        return vodYear;
    }

    public String getVodArea() {
        return vodArea;
    }

    public String getVodActor() {
        return vodActor;
    }

    public String getVodDirector() {
        return vodDirector;
    }

    public String getVodContent() {
        return vodContent;
    }

    public String getVodPlayFrom() {
        return vodPlayFrom;
    }

    public String getVodPlayUrl() {
        return vodPlayUrl;
    }

    public String getVodTag() {
        return vodTag;
    }

    public String getAction() {
        return action;
    }

    public Style getStyle() {
        return style;
    }

    // ============================ Setter 方法 ============================

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public void setVodYear(String vodYear) {
        this.vodYear = vodYear;
    }

    public void setVodArea(String vodArea) {
        this.vodArea = vodArea;
    }

    public void setVodActor(String vodActor) {
        this.vodActor = vodActor;
    }

    public void setVodDirector(String vodDirector) {
        this.vodDirector = vodDirector;
    }

    public void setVodContent(String vodContent) {
        this.vodContent = vodContent;
    }

    public void setVodPlayFrom(String vodPlayFrom) {
        this.vodPlayFrom = vodPlayFrom;
    }

    public void setVodPlayUrl(String vodPlayUrl) {
        this.vodPlayUrl = vodPlayUrl;
    }

    public void setVodTag(String vodTag) {
        this.vodTag = vodTag;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    // ============================ Style 内部类 ============================

    public static class Style {

        @SerializedName("type")
        private String type;

        @SerializedName("ratio")
        private Float ratio;

        public static Style rect() {
            return rect(0.75f);
        }

        public static Style rect(float ratio) {
            return new Style("rect", ratio);
        }

        public static Style oval() {
            return new Style("oval", 1.0f);
        }

        public static Style full() {
            return new Style("full");
        }

        public static Style list() {
            return new Style("list");
        }

        public Style(String type) {
            this.type = type;
        }

        public Style(String type, Float ratio) {
            this.type = type;
            this.ratio = ratio;
        }

        public String getType() {
            return type;
        }

        public Float getRatio() {
            return ratio;
        }
    }

    // ============================ VodPlayBuilder 播放源构建器 ============================

    public static class VodPlayBuilder {
        private final List<String> vodPlayFrom = new ArrayList<>();
        private final List<String> vodPlayUrl = new ArrayList<>();

        /**
         * 添加一个播放源分组
         *
         * @param playFrom 播放源名称（如 "线路1"）
         * @param playUrl  该源下的剧集列表
         * @return this
         */
        public VodPlayBuilder append(String playFrom, List<PlayUrl> playUrl) {
            vodPlayFrom.add(playFrom);
            vodPlayUrl.add(toPlayUrlStr(playUrl));
            return this;
        }

        public BuildResult build() {
            BuildResult result = new BuildResult();
            result.vodPlayFrom = String.join("$$$", vodPlayFrom);
            result.vodPlayUrl = String.join("$$$", vodPlayUrl);
            return result;
        }

        private String toPlayUrlStr(List<PlayUrl> playUrl) {
            List<String> list = new ArrayList<>();
            for (PlayUrl url : playUrl) {
                // 去掉名称中的 "m3u8" 后缀（常见于某些源）
                String name = url.name.replace("m3u8", "").trim();
                list.add(name + "$" + url.url);
            }
            return String.join("#", list);
        }

        public static class BuildResult {
            public String vodPlayFrom;
            public String vodPlayUrl;
        }

        public static class PlayUrl {
            public String flag;  // 线路标志（可选）
            public String name;  // 剧集名称，如 "第01集"
            public String url;   // 播放地址
        }
    }
}
