package com.github.catvod.bean;

import com.github.catvod.utils.Json;
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

    public static Vod objectFrom(String str) {
        try {
            Vod item = Json.getGson().fromJson(str, Vod.class);
            return item == null ? new Vod() : item;
        } catch (Exception e) {
            // 记录异常但不影响程序运行
            e.printStackTrace();
            return new Vod();
        }
    }

    public static Vod action(String action) {
        Vod vod = new Vod();
        vod.action = action;
        return vod;
    }

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
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setStyle(style);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style, String action) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setStyle(style);
        setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, boolean folder) {
        setVodId(vodId);
        setVodName(vodName);
        setVodPic(vodPic);
        setVodRemarks(vodRemarks);
        setVodTag(folder ? "folder" : "file");
    }

    public String getTypeName() {
        return typeName != null ? typeName : "";
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getVodId() {
        return vodId != null ? vodId : "";
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getVodName() {
        return vodName != null ? vodName : "";
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic != null ? vodPic : "";
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodRemarks() {
        return vodRemarks != null ? vodRemarks : "";
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getVodYear() {
        return vodYear != null ? vodYear : "";
    }

    public void setVodYear(String vodYear) {
        this.vodYear = vodYear;
    }

    public String getVodArea() {
        return vodArea != null ? vodArea : "";
    }

    public void setVodArea(String vodArea) {
        this.vodArea = vodArea;
    }

    public String getVodActor() {
        return vodActor != null ? vodActor : "";
    }

    public void setVodActor(String vodActor) {
        this.vodActor = vodActor;
    }

    public String getVodDirector() {
        return vodDirector != null ? vodDirector : "";
    }

    public void setVodDirector(String vodDirector) {
        this.vodDirector = vodDirector;
    }

    public String getVodContent() {
        return vodContent != null ? vodContent : "";
    }

    public void setVodContent(String vodContent) {
        this.vodContent = vodContent;
    }

    public String getVodPlayFrom() {
        return vodPlayFrom != null ? vodPlayFrom : "";
    }

    public void setVodPlayFrom(String vodPlayFrom) {
        this.vodPlayFrom = vodPlayFrom;
    }

    public String getVodPlayUrl() {
        return vodPlayUrl != null ? vodPlayUrl : "";
    }

    public void setVodPlayUrl(String vodPlayUrl) {
        this.vodPlayUrl = vodPlayUrl;
    }

    public String getVodTag() {
        return vodTag != null ? vodTag : "";
    }

    public void setVodTag(String vodTag) {
        this.vodTag = vodTag;
    }

    public String getAction() {
        return action != null ? action : "";
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

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
            return type != null ? type : "";
        }

        public void setType(String type) {
            this.type = type;
        }

        public Float getRatio() {
            return ratio;
        }

        public void setRatio(Float ratio) {
            this.ratio = ratio;
        }
    }
    
    public static class VodPlayBuilder {
        private final List<String> fromList = new ArrayList<>();
        private final List<String> urlList = new ArrayList<>();

        public static class PlayUrl {
            public String name;
            public String url;

            public PlayUrl() {}

            public PlayUrl(String name, String url) {
                this.name = name;
                this.url = url;
            }
        }

        /**
         * 对应 DJW.java 中调用的 builder.append(from, pus)
         */
        public void append(String from, List<PlayUrl> urls) {
            fromList.add(from);
            List<String> urlStrings = new ArrayList<>();
            for (PlayUrl pu : urls) {
                // CatVod 规范：集名$链接
                urlStrings.add(pu.name + "$" + pu.url);
            }
            // CatVod 规范：集与集之间用 # 分隔
            urlList.add(join("#", urlStrings));
        }

        public static class BuildResult {
            // DJW.java 访问了这两个字段
            public String vodPlayFrom;
            public String vodPlayUrl;
        }

        public BuildResult build() {
            BuildResult br = new BuildResult();
            // CatVod 规范：不同播放源之间用 $$$ 分隔
            br.vodPlayFrom = join("$$$", fromList);
            br.vodPlayUrl = join("$$$", urlList);
            return br;
        }

        // 内部辅助方法，替代 android.text.TextUtils.join
        private String join(String delimiter, List<String> tokens) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String token : tokens) {
                if (first) first = false;
                else sb.append(delimiter);
                sb.append(token);
            }
            return sb.toString();
        }
    }
}